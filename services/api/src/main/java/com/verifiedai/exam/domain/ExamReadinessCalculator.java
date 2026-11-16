package com.verifiedai.exam.domain;

import com.verifiedai.exam.domain.ExamModels.ExamDefinition;
import com.verifiedai.exam.domain.ExamModels.ExamSectionSpec;
import com.verifiedai.exam.domain.ExamModels.ExamTopicWeight;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Pure, stateless, deterministic psychometric readiness calculator (INV-EXAM-004: $0.00 LLM cost).
 * Implements Learning OS Quantity 5 (86_LEARNING_OUTCOMES Section 4.5), 90% credible intervals,
 * sparse-evidence PRELIMINARY guard (INV-EXAM-001), active-trap penalty attribution,
 * and high-ROI study allocation ranking.
 */
@Component
public class ExamReadinessCalculator {

    public static final String METHODOLOGY_VERSION = "EXAM_READINESS_V1";
    private static final BigDecimal CREDIBLE_LEVEL = new BigDecimal("0.90");
    private static final double Z_90 = 1.64485;
    private static final double COLD_START_MASTERY = 0.35;
    private static final double COLD_START_CONFIDENCE = 0.05;
    private static final double COLD_START_RETENTION = 1.00;
    private static final double COLD_START_HALF_LIFE_DAYS = 14.0;
    private static final double DEFAULT_PACING_FACTOR = 0.9200;
    private static final double DEFAULT_TRAP_BETA = 0.45;
    private static final double MAX_TRAP_PENALTY = 0.45;
    private static final int MAX_ROI_ALLOCATIONS = 5;

    public record SkillMasterySignal(
            String skillCode,
            double masteryScore,
            double halfLifeDays,
            double confidence,
            int evidenceCount,
            Instant lastEvidenceAt
    ) {
    }

    public record ActiveTrapSignal(
            String mistakeCode,
            String trapFamilyCode,
            String skillCode,
            double posteriorRate,
            int occurrenceCount
    ) {
    }

    public record MockScoreSignal(
            UUID sessionId,
            BigDecimal netScore,
            int totalQuestions,
            int correctCount,
            int wrongCount,
            int blankCount,
            int totalTimeSpentSeconds,
            int expectedDurationSeconds,
            Instant scoredAt
    ) {
    }

    public record CalculationInput(
            UUID projectionId,
            UUID userId,
            ExamDefinition examDefinition,
            UUID targetId,
            BigDecimal targetNetScore,
            Map<String, SkillMasterySignal> masteryBySkill,
            List<ActiveTrapSignal> activeTraps,
            List<MockScoreSignal> recentMockScores,
            Instant asOf
    ) {
        public CalculationInput {
            Objects.requireNonNull(userId, "userId is required");
            Objects.requireNonNull(examDefinition, "examDefinition is required");
            masteryBySkill = masteryBySkill == null ? Map.of() : Map.copyOf(masteryBySkill);
            activeTraps = activeTraps == null ? List.of() : List.copyOf(activeTraps);
            recentMockScores = recentMockScores == null ? List.of() : List.copyOf(recentMockScores);
            asOf = asOf == null ? Instant.now() : asOf;
            projectionId = projectionId == null ? UUID.randomUUID() : projectionId;
        }
    }

    private record NormalizedSkillWeight(
            String sectionCode,
            String skillCode,
            String domainCode,
            double questionWeight,
            double fractionWeight
    ) {
    }

    private record SkillEvaluation(
            String sectionCode,
            String skillCode,
            double questionWeight,
            double fractionWeight,
            double mastery,
            double retention,
            double confidence,
            double trapPenalty,
            double pCorrect,
            double pWrong,
            double expectedNet,
            double trapNetLoss,
            double aleatoricVariance,
            double roiPerHour,
            ExamReadinessRationaleCode rationaleCode,
            int recommendedMinutes
    ) {
    }

    private static final class TrapAccumulator {
        final String trapFamilyCode;
        double maxPosteriorRate = 0.0;
        double accumulatedNetLoss = 0.0;
        final Set<String> affectedSkills = new LinkedHashSet<>();

        TrapAccumulator(String trapFamilyCode) {
            this.trapFamilyCode = trapFamilyCode;
        }
    }

    public ExamReadinessProjection calculate(CalculationInput input) {
        Objects.requireNonNull(input, "input is required");

        ExamDefinition definition = input.examDefinition();
        int totalQuestions = Math.max(1, definition.totalQuestions());
        BigDecimal penaltyRatioBd = definition.wrongPenaltyFactor() == null
                ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
                : definition.wrongPenaltyFactor().setScale(4, RoundingMode.HALF_UP);
        double gamma = clamp(penaltyRatioBd.doubleValue(), 0.0, 1.0);

        List<NormalizedSkillWeight> weights = normalizeWeights(definition, totalQuestions);
        double pacingFactor = computePacingFactor(input.recentMockScores());

        Map<String, TrapAccumulator> trapAccumulators = new LinkedHashMap<>();
        List<SkillEvaluation> skillEvals = new ArrayList<>(weights.size());

        double expectedSkillNetSum = 0.0;
        double netLostToTrapsSum = 0.0;
        double weightedRetentionSum = 0.0;
        double weightedConfidenceSum = 0.0;
        double aleatoricVarianceSum = 0.0;
        int evaluatedSkillCount = 0;
        int totalEvidenceCount = 0;

        for (NormalizedSkillWeight weight : weights) {
            SkillMasterySignal masterySignal = input.masteryBySkill().get(weight.skillCode());
            double mastery;
            double confidence;
            double retention;
            if (masterySignal == null) {
                mastery = COLD_START_MASTERY;
                confidence = COLD_START_CONFIDENCE;
                retention = COLD_START_RETENTION;
            } else {
                mastery = clamp(masterySignal.masteryScore(), 0.05, 0.99);
                confidence = clamp(masterySignal.confidence(), 0.0, 1.0);
                double halfLife = Math.max(1.0, masterySignal.halfLifeDays() > 0 ? masterySignal.halfLifeDays() : COLD_START_HALF_LIFE_DAYS);
                double deltaDays = computeElapsedDays(masterySignal.lastEvidenceAt(), input.asOf());
                retention = clamp(Math.pow(2.0, -deltaDays / halfLife), 0.0, 1.0);
                if (masterySignal.evidenceCount() > 0) {
                    evaluatedSkillCount++;
                    totalEvidenceCount += masterySignal.evidenceCount();
                }
            }

            List<ActiveTrapSignal> matchingTraps = resolveMatchingTraps(weight, weights, input.activeTraps());
            double rawTrapPenaltySum = 0.0;
            Map<String, Double> trapShareByFamily = new LinkedHashMap<>();
            for (ActiveTrapSignal trap : matchingTraps) {
                double rate = clamp(trap.posteriorRate(), 0.0, 1.0);
                double contribution = DEFAULT_TRAP_BETA * rate;
                rawTrapPenaltySum += contribution;
                String family = normalizeTrapFamily(trap.trapFamilyCode(), trap.mistakeCode());
                trapShareByFamily.merge(family, contribution, Double::sum);
                TrapAccumulator acc = trapAccumulators.computeIfAbsent(family, TrapAccumulator::new);
                acc.maxPosteriorRate = Math.max(acc.maxPosteriorRate, rate);
                acc.affectedSkills.add(weight.skillCode());
            }

            double piTrap = clamp(rawTrapPenaltySum, 0.0, MAX_TRAP_PENALTY);
            double trapNetLoss = weight.questionWeight() * (1.0 + gamma) * mastery * retention * piTrap * pacingFactor;

            if (rawTrapPenaltySum > 0.0 && trapNetLoss > 0.0) {
                for (Map.Entry<String, Double> entry : trapShareByFamily.entrySet()) {
                    double share = entry.getValue() / rawTrapPenaltySum;
                    trapAccumulators.get(entry.getKey()).accumulatedNetLoss += trapNetLoss * share;
                }
            }

            double pCorrect = clamp(mastery * retention * (1.0 - piTrap) * pacingFactor, 0.0, 1.0);
            double pWrong = clamp((1.0 - pCorrect) * (0.20 + 0.60 * piTrap), 0.0, 1.0 - pCorrect);
            double skillNet = weight.questionWeight() * (pCorrect - gamma * pWrong);
            double aleatoricVar = weight.questionWeight() * (1.0 + gamma) * (1.0 + gamma) * pCorrect * (1.0 - pCorrect);

            double lambdaLearn = 0.35 + 0.25 * (1.0 - confidence) + 0.40 * piTrap;
            double roiPerHour = weight.questionWeight() * (1.0 + gamma) * (1.0 - pCorrect) * lambdaLearn;
            ExamReadinessRationaleCode rationaleCode = selectRationaleCode(piTrap, retention, confidence);
            int studyMinutes = (int) Math.round(clamp(30.0 + 30.0 * (1.0 - pCorrect), 20.0, 90.0));

            skillEvals.add(new SkillEvaluation(
                    weight.sectionCode(),
                    weight.skillCode(),
                    weight.questionWeight(),
                    weight.fractionWeight(),
                    mastery,
                    retention,
                    confidence,
                    piTrap,
                    pCorrect,
                    pWrong,
                    skillNet,
                    trapNetLoss,
                    aleatoricVar,
                    roiPerHour,
                    rationaleCode,
                    studyMinutes
            ));

            expectedSkillNetSum += skillNet;
            netLostToTrapsSum += trapNetLoss;
            weightedRetentionSum += weight.fractionWeight() * retention;
            weightedConfidenceSum += weight.fractionWeight() * confidence;
            aleatoricVarianceSum += aleatoricVar;
        }

        List<MockScoreSignal> mocks = input.recentMockScores();
        int completedMockCount = mocks.size();
        totalEvidenceCount += mocks.stream().mapToInt(m -> Math.max(0, m.correctCount() + m.wrongCount())).sum();
        UUID latestScoredSessionId = mocks.stream()
                .max(Comparator.comparing(MockScoreSignal::scoredAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(MockScoreSignal::sessionId)
                .orElse(null);

        double rawMid = expectedSkillNetSum;
        if (completedMockCount > 0) {
            double mockAvgScaledNet = mocks.stream()
                    .mapToDouble(m -> {
                        int mockTotal = Math.max(1, m.totalQuestions());
                        double rawMockNet = m.netScore() == null ? 0.0 : m.netScore().doubleValue();
                        return rawMockNet * ((double) totalQuestions / mockTotal);
                    })
                    .average()
                    .orElse(expectedSkillNetSum);
            double alphaMock = Math.min(0.60, completedMockCount / (completedMockCount + 3.0));
            rawMid = (1.0 - alphaMock) * expectedSkillNetSum + alphaMock * mockAvgScaledNet;
        }

        double epistemicConfidenceVal = clamp(
                weightedConfidenceSum + Math.min(0.25, 0.08 * completedMockCount),
                0.0,
                1.0
        );
        BigDecimal epistemicConfidenceBd = BigDecimal.valueOf(epistemicConfidenceVal).setScale(4, RoundingMode.HALF_UP);
        ExamReadinessConfidenceTier confidenceTier = resolveConfidenceTier(epistemicConfidenceBd);
        boolean calibrationRequired = confidenceTier == ExamReadinessConfidenceTier.PRELIMINARY;
        int recommendedDiagnosticCount = resolveRecommendedDiagnosticCount(confidenceTier);

        double sigmaEpistemic = totalQuestions * (1.0 - epistemicConfidenceBd.doubleValue()) * 0.18;
        double sigmaAleatoric = Math.sqrt(Math.max(0.0, aleatoricVarianceSum));
        double delta90 = Math.max(0.50, Z_90 * Math.hypot(sigmaEpistemic, sigmaAleatoric));
        if (confidenceTier == ExamReadinessConfidenceTier.PRELIMINARY) {
            delta90 = Math.max(delta90, 0.15 * totalQuestions);
        }

        BigDecimal minPossibleNet = BigDecimal.valueOf(totalQuestions)
                .multiply(penaltyRatioBd)
                .negate()
                .setScale(2, RoundingMode.CEILING);
        BigDecimal maxPossibleNet = BigDecimal.valueOf(totalQuestions).setScale(2, RoundingMode.FLOOR);

        BigDecimal midBd = clampBigDecimal(
                BigDecimal.valueOf(rawMid).setScale(2, RoundingMode.HALF_UP),
                minPossibleNet,
                maxPossibleNet
        );
        BigDecimal deltaBd = BigDecimal.valueOf(delta90).setScale(2, RoundingMode.HALF_UP);
        BigDecimal lowBd = clampBigDecimal(midBd.subtract(deltaBd), minPossibleNet, midBd);
        BigDecimal highBd = clampBigDecimal(midBd.add(deltaBd), midBd, maxPossibleNet);

        BigDecimal targetNetScore = input.targetNetScore() != null
                ? input.targetNetScore().setScale(2, RoundingMode.HALF_UP)
                : null;
        BigDecimal netGapToTarget = targetNetScore != null
                ? targetNetScore.subtract(midBd).setScale(2, RoundingMode.HALF_UP)
                : null;

        BigDecimal netLostToTrapsBd = BigDecimal.valueOf(Math.max(0.0, netLostToTrapsSum))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal pacingFactorBd = BigDecimal.valueOf(pacingFactor).setScale(4, RoundingMode.HALF_UP);
        BigDecimal meanRetentionBd = BigDecimal.valueOf(clamp(weightedRetentionSum, 0.0, 1.0))
                .setScale(4, RoundingMode.HALF_UP);

        List<ActiveTrapImpactItem> activeTrapBreakdown = buildTrapBreakdown(trapAccumulators);
        List<SectionReadinessBreakdown> sectionReadiness = buildSectionReadiness(
                definition,
                skillEvals,
                gamma,
                completedMockCount,
                confidenceTier
        );
        List<ExamReadinessTopicAllocation> topRoiAllocations = buildTopRoiAllocations(
                input.projectionId(),
                skillEvals
        );

        return new ExamReadinessProjection(
                input.projectionId(),
                input.userId(),
                definition.examCode(),
                input.targetId(),
                latestScoredSessionId,
                totalQuestions,
                penaltyRatioBd,
                lowBd,
                midBd,
                highBd,
                CREDIBLE_LEVEL,
                targetNetScore,
                netGapToTarget,
                netLostToTrapsBd,
                pacingFactorBd,
                meanRetentionBd,
                epistemicConfidenceBd,
                confidenceTier,
                calibrationRequired,
                recommendedDiagnosticCount,
                evaluatedSkillCount,
                totalEvidenceCount,
                completedMockCount,
                activeTrapBreakdown,
                sectionReadiness,
                topRoiAllocations,
                METHODOLOGY_VERSION,
                false,
                0,
                BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP),
                input.asOf()
        );
    }

    private static List<NormalizedSkillWeight> normalizeWeights(ExamDefinition definition, int totalQuestions) {
        List<ExamTopicWeight> rawWeights = definition.topicWeights();
        if (rawWeights == null || rawWeights.isEmpty()) {
            List<ExamSectionSpec> sections = definition.sections();
            if (sections != null && !sections.isEmpty()) {
                int sectionQuestionSum = Math.max(1, sections.stream().mapToInt(ExamSectionSpec::questionCount).sum());
                List<NormalizedSkillWeight> fallback = new ArrayList<>(sections.size());
                for (ExamSectionSpec sec : sections) {
                    double frac = (double) Math.max(1, sec.questionCount()) / sectionQuestionSum;
                    fallback.add(new NormalizedSkillWeight(
                            sec.sectionCode(),
                            "MATH.GENERAL." + sec.sectionCode(),
                            "MATH.GENERAL",
                            totalQuestions * frac,
                            frac
                    ));
                }
                return fallback;
            }
            return List.of(new NormalizedSkillWeight(
                    "GENERAL",
                    "MATH.GENERAL.CORE",
                    "MATH.GENERAL",
                    totalQuestions,
                    1.0
            ));
        }

        double totalWeightSum = rawWeights.stream()
                .mapToDouble(w -> w.questionCountWeight() == null ? 0.0 : Math.max(0.0, w.questionCountWeight().doubleValue()))
                .sum();
        if (totalWeightSum <= 0.0) {
            double evenFraction = 1.0 / rawWeights.size();
            return rawWeights.stream()
                    .map(w -> new NormalizedSkillWeight(
                            w.sectionCode(),
                            w.skillId(),
                            w.domainCode(),
                            totalQuestions * evenFraction,
                            evenFraction
                    ))
                    .toList();
        }

        return rawWeights.stream()
                .map(w -> {
                    double raw = w.questionCountWeight() == null ? 0.0 : Math.max(0.0, w.questionCountWeight().doubleValue());
                    double frac = raw / totalWeightSum;
                    return new NormalizedSkillWeight(
                            w.sectionCode(),
                            w.skillId(),
                            w.domainCode(),
                            totalQuestions * frac,
                            frac
                    );
                })
                .toList();
    }

    private static double computePacingFactor(List<MockScoreSignal> mocks) {
        if (mocks == null || mocks.isEmpty()) {
            return DEFAULT_PACING_FACTOR;
        }
        double blankRateSum = 0.0;
        double timeOverrunSum = 0.0;
        for (MockScoreSignal mock : mocks) {
            int total = Math.max(1, mock.totalQuestions());
            blankRateSum += (double) Math.max(0, mock.blankCount()) / total;
            if (mock.expectedDurationSeconds() > 0 && mock.totalTimeSpentSeconds() > 0) {
                double ratio = (double) mock.totalTimeSpentSeconds() / mock.expectedDurationSeconds();
                timeOverrunSum += Math.max(0.0, ratio - 0.95);
            }
        }
        double meanBlankRate = blankRateSum / mocks.size();
        double meanOverrun = timeOverrunSum / mocks.size();
        return clamp(1.0 - 0.30 * meanBlankRate - 0.15 * meanOverrun, 0.7000, 1.0000);
    }

    private static List<ActiveTrapSignal> resolveMatchingTraps(
            NormalizedSkillWeight targetWeight,
            List<NormalizedSkillWeight> allWeights,
            List<ActiveTrapSignal> activeTraps
    ) {
        if (activeTraps == null || activeTraps.isEmpty()) {
            return List.of();
        }
        Set<String> examSkills = new LinkedHashSet<>();
        for (NormalizedSkillWeight w : allWeights) {
            examSkills.add(w.skillCode().toUpperCase(Locale.ROOT));
        }

        List<ActiveTrapSignal> matched = new ArrayList<>();
        String targetSkillUpper = targetWeight.skillCode().toUpperCase(Locale.ROOT);
        String targetDomainUpper = targetWeight.domainCode() == null
                ? ""
                : targetWeight.domainCode().toUpperCase(Locale.ROOT);

        for (ActiveTrapSignal trap : activeTraps) {
            String trapSkillUpper = trap.skillCode() == null ? "" : trap.skillCode().trim().toUpperCase(Locale.ROOT);
            if (trapSkillUpper.equals(targetSkillUpper)) {
                matched.add(trap);
            } else if (!trapSkillUpper.isEmpty() && !examSkills.contains(trapSkillUpper)) {
                if (!targetDomainUpper.isEmpty()
                        && (trapSkillUpper.startsWith(targetDomainUpper) || targetSkillUpper.startsWith(trapSkillUpper))) {
                    matched.add(trap);
                }
            }
        }
        return matched;
    }

    private static String normalizeTrapFamily(String rawFamily, String mistakeCode) {
        if (rawFamily != null && !rawFamily.isBlank()) {
            return rawFamily.trim().toUpperCase(Locale.ROOT);
        }
        if (mistakeCode != null && !mistakeCode.isBlank()) {
            return mistakeCode.trim().toUpperCase(Locale.ROOT);
        }
        return "GENERAL_PROCESS_TRAP";
    }

    private static List<ActiveTrapImpactItem> buildTrapBreakdown(Map<String, TrapAccumulator> accumulators) {
        return accumulators.values().stream()
                .sorted(Comparator.comparingDouble((TrapAccumulator a) -> a.accumulatedNetLoss)
                        .reversed()
                        .thenComparing(a -> a.trapFamilyCode))
                .map(acc -> new ActiveTrapImpactItem(
                        acc.trapFamilyCode,
                        "exam.readiness.trap." + acc.trapFamilyCode.toLowerCase(Locale.ROOT),
                        BigDecimal.valueOf(clamp(acc.maxPosteriorRate, 0.0, 1.0)).setScale(4, RoundingMode.HALF_UP),
                        BigDecimal.valueOf(Math.max(0.0, acc.accumulatedNetLoss)).setScale(2, RoundingMode.HALF_UP),
                        acc.affectedSkills.size()
                ))
                .toList();
    }

    private static List<SectionReadinessBreakdown> buildSectionReadiness(
            ExamDefinition definition,
            List<SkillEvaluation> skillEvals,
            double gamma,
            int completedMockCount,
            ExamReadinessConfidenceTier overallTier
    ) {
        Map<String, ExamSectionSpec> specByCode = new LinkedHashMap<>();
        if (definition.sections() != null) {
            for (ExamSectionSpec spec : definition.sections()) {
                specByCode.put(spec.sectionCode(), spec);
            }
        }

        Map<String, List<SkillEvaluation>> bySection = new LinkedHashMap<>();
        for (SkillEvaluation eval : skillEvals) {
            bySection.computeIfAbsent(eval.sectionCode(), ignored -> new ArrayList<>()).add(eval);
        }

        List<SectionReadinessBreakdown> result = new ArrayList<>(bySection.size());
        for (Map.Entry<String, List<SkillEvaluation>> entry : bySection.entrySet()) {
            String sectionCode = entry.getKey();
            List<SkillEvaluation> evals = entry.getValue();
            ExamSectionSpec spec = specByCode.get(sectionCode);

            double qSum = evals.stream().mapToDouble(SkillEvaluation::questionWeight).sum();
            int secQuestions = spec != null && spec.questionCount() > 0
                    ? spec.questionCount()
                    : Math.max(1, (int) Math.round(qSum));

            double secNetMid = evals.stream().mapToDouble(SkillEvaluation::expectedNet).sum();
            double secAleatoric = Math.sqrt(Math.max(0.0, evals.stream().mapToDouble(SkillEvaluation::aleatoricVariance).sum()));

            double weightTotal = Math.max(1e-9, qSum);
            double meanMastery = evals.stream().mapToDouble(e -> e.mastery() * e.questionWeight()).sum() / weightTotal;
            double meanRetention = evals.stream().mapToDouble(e -> e.retention() * e.questionWeight()).sum() / weightTotal;
            double meanConf = evals.stream().mapToDouble(e -> e.confidence() * e.questionWeight()).sum() / weightTotal;
            double secConf = clamp(meanConf + Math.min(0.25, 0.08 * completedMockCount), 0.0, 1.0);

            double sigmaEpistemic = secQuestions * (1.0 - secConf) * 0.18;
            double delta = Math.max(0.25, Z_90 * Math.hypot(sigmaEpistemic, secAleatoric));
            if (overallTier == ExamReadinessConfidenceTier.PRELIMINARY) {
                delta = Math.max(delta, 0.15 * secQuestions);
            }

            BigDecimal minSec = BigDecimal.valueOf(secQuestions * gamma).negate().setScale(2, RoundingMode.CEILING);
            BigDecimal maxSec = BigDecimal.valueOf(secQuestions).setScale(2, RoundingMode.FLOOR);
            BigDecimal midBd = clampBigDecimal(BigDecimal.valueOf(secNetMid).setScale(2, RoundingMode.HALF_UP), minSec, maxSec);
            BigDecimal deltaBd = BigDecimal.valueOf(delta).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lowBd = clampBigDecimal(midBd.subtract(deltaBd), minSec, midBd);
            BigDecimal highBd = clampBigDecimal(midBd.add(deltaBd), midBd, maxSec);

            String titleKey = spec != null && spec.titleEn() != null && !spec.titleEn().isBlank()
                    ? spec.titleEn()
                    : sectionCode;

            result.add(new SectionReadinessBreakdown(
                    sectionCode,
                    titleKey,
                    secQuestions,
                    lowBd,
                    midBd,
                    highBd,
                    BigDecimal.valueOf(clamp(meanMastery, 0.0, 1.0)).setScale(4, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(clamp(meanRetention, 0.0, 1.0)).setScale(4, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(secConf).setScale(4, RoundingMode.HALF_UP)
            ));
        }
        return result;
    }

    private static List<ExamReadinessTopicAllocation> buildTopRoiAllocations(
            UUID projectionId,
            List<SkillEvaluation> evals
    ) {
        List<SkillEvaluation> ranked = evals.stream()
                .sorted(Comparator.comparingDouble(SkillEvaluation::roiPerHour)
                        .reversed()
                        .thenComparing(Comparator.comparingDouble(SkillEvaluation::questionWeight).reversed())
                        .thenComparing(SkillEvaluation::skillCode))
                .limit(MAX_ROI_ALLOCATIONS)
                .toList();

        List<ExamReadinessTopicAllocation> allocations = new ArrayList<>(ranked.size());
        for (int i = 0; i < ranked.size(); i++) {
            SkillEvaluation eval = ranked.get(i);
            int rank = i + 1;
            UUID allocationId = UUID.nameUUIDFromBytes(
                    (projectionId + ":" + eval.sectionCode() + ":" + eval.skillCode() + ":" + rank)
                            .getBytes(StandardCharsets.UTF_8)
            );
            allocations.add(new ExamReadinessTopicAllocation(
                    allocationId,
                    eval.sectionCode(),
                    eval.skillCode(),
                    formatTopicTitleKey(eval.skillCode()),
                    BigDecimal.valueOf(Math.max(0.0, eval.questionWeight())).setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(clamp(eval.mastery(), 0.0, 1.0)).setScale(4, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(clamp(eval.retention(), 0.0, 1.0)).setScale(4, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(Math.max(0.0, eval.roiPerHour())).setScale(3, RoundingMode.HALF_UP),
                    eval.recommendedMinutes(),
                    rank,
                    eval.rationaleCode(),
                    false
            ));
        }
        return allocations;
    }

    private static ExamReadinessRationaleCode selectRationaleCode(
            double trapPenalty,
            double retention,
            double confidence
    ) {
        if (trapPenalty >= 0.08) {
            return ExamReadinessRationaleCode.ACTIVE_TRAP_MITIGATION;
        }
        if (retention < 0.75) {
            return ExamReadinessRationaleCode.RETENTION_DECAY_RECOVERY;
        }
        if (confidence < 0.25) {
            return ExamReadinessRationaleCode.CALIBRATION_EVIDENCE_GAP;
        }
        return ExamReadinessRationaleCode.HIGH_BLUEPRINT_WEIGHT_LOW_MASTERY;
    }

    private static ExamReadinessConfidenceTier resolveConfidenceTier(BigDecimal confidence) {
        if (confidence.compareTo(new BigDecimal("0.2500")) < 0) {
            return ExamReadinessConfidenceTier.PRELIMINARY;
        }
        if (confidence.compareTo(new BigDecimal("0.5000")) < 0) {
            return ExamReadinessConfidenceTier.CALIBRATING;
        }
        if (confidence.compareTo(new BigDecimal("0.7500")) < 0) {
            return ExamReadinessConfidenceTier.ESTABLISHED;
        }
        return ExamReadinessConfidenceTier.HIGH_CONFIDENCE;
    }

    private static int resolveRecommendedDiagnosticCount(ExamReadinessConfidenceTier tier) {
        return switch (tier) {
            case PRELIMINARY -> 10;
            case CALIBRATING -> 5;
            case ESTABLISHED, HIGH_CONFIDENCE -> 0;
        };
    }

    private static double computeElapsedDays(Instant lastEvidenceAt, Instant asOf) {
        if (lastEvidenceAt == null || asOf == null || !asOf.isAfter(lastEvidenceAt)) {
            return 0.0;
        }
        Duration elapsed = Duration.between(lastEvidenceAt, asOf);
        return Math.max(0.0, elapsed.toSeconds() / 86400.0);
    }

    private static String formatTopicTitleKey(String skillCode) {
        return "topic." + skillCode.trim().toLowerCase(Locale.ROOT);
    }

    private static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    private static BigDecimal clampBigDecimal(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value.compareTo(min) < 0) {
            return min;
        }
        if (value.compareTo(max) > 0) {
            return max;
        }
        return value;
    }
}
