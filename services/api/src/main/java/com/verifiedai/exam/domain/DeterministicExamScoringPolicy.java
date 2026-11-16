package com.verifiedai.exam.domain;

import com.verifiedai.exam.domain.ExamModels.ExamDefinition;
import com.verifiedai.exam.domain.ExamModels.ScoringEvaluationResult;
import com.verifiedai.exam.domain.ExamModels.ScoringFormulaType;
import com.verifiedai.exam.domain.ExamModels.SectionWeightEntry;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * INV-EXAM-004: 100% deterministic server-side scoring engine and per-item
 * evidence traceability builder ($0.00 LLM cost, 0 tokens, trainingEligible=false).
 */
@Component
public class DeterministicExamScoringPolicy {

    private static final BigDecimal ZERO_2 = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_2 = BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal ZERO_6 = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);

    public ExamSessionScoreSnapshot scoreSession(
            UUID scoreId,
            ExamSession session,
            ExamDefinition definition,
            Map<UUID, QuestionBankItem> questionsById,
            int scoreVersion,
            Instant sessionSubmittedAt,
            Instant scoredAt,
            Instant lastRegradedAt
    ) {
        Objects.requireNonNull(scoreId, "scoreId is required");
        Objects.requireNonNull(session, "session is required");
        Objects.requireNonNull(definition, "definition is required");
        Objects.requireNonNull(questionsById, "questionsById is required");
        Objects.requireNonNull(sessionSubmittedAt, "sessionSubmittedAt is required");
        Objects.requireNonNull(scoredAt, "scoredAt is required");

        List<ExamSessionItem> orderedItems = session.items().stream()
                .sorted(Comparator.comparingInt(ExamSessionItem::itemPosition))
                .toList();
        if (orderedItems.isEmpty()) {
            throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.EXAM_SESSION_NOT_SCORABLE,
                    "Exam session contains no items to score: " + session.id(),
                    false,
                    "ASSEMBLE_NON_EMPTY_SESSION"
            );
        }

        int totalQuestions = orderedItems.size();
        ScoringFormulaType formulaType = definition.scoringFormulaType();
        BigDecimal wrongPenaltyRatio = ScoringFormulaPolicy.effectiveWrongPenaltyRatio(definition);
        Map<String, BigDecimal> sectionPointWeights = computeSectionItemPointMultipliers(definition, orderedItems);

        int correctCount = 0;
        int wrongCount = 0;
        int blankCount = 0;
        int totalTimeSpentSeconds = 0;
        BigDecimal weightedRawSum = BigDecimal.ZERO;
        BigDecimal weightedPenaltySum = BigDecimal.ZERO;
        BigDecimal weightedMaxPossible = BigDecimal.ZERO;

        List<ExamSessionItemEvaluation> itemEvaluations = new ArrayList<>(totalQuestions);
        Map<String, TopicAccumulator> topicAccumulators = new LinkedHashMap<>();
        EnumMap<DifficultyBand, DifficultyAccumulator> difficultyAccumulators = new EnumMap<>(DifficultyBand.class);

        for (ExamSessionItem item : orderedItems) {
            QuestionBankItem question = questionsById.get(item.questionItemId());
            if (question == null) {
                throw new ApiProblemException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ApiErrorCode.EXAM_QUESTION_ITEM_NOT_FOUND,
                        "Question bank item missing for session item: " + item.questionItemId(),
                        false,
                        "ENSURE_QUESTION_BANK_INTEGRITY"
                );
            }

            QuestionFormatType formatType = QuestionFormatType.fromQuestionItemFormat(question.itemFormat());
            DifficultyBand difficultyBand = DifficultyBand.fromQuestionDifficultyBand(question.difficultyBand());
            CognitiveLevel cognitiveLevel = CognitiveLevel.fromCognitiveTaxonomyLevel(question.cognitiveLevel());

            BigDecimal itemPointWeight = sectionPointWeights.getOrDefault(
                    item.sectionCode() == null ? "" : item.sectionCode(),
                    ONE_2
            );
            weightedMaxPossible = weightedMaxPossible.add(itemPointWeight);

            EvaluationDecision decision = evaluateItemResponse(item, question, formatType);

            BigDecimal rawPointsAwarded;
            BigDecimal penaltyPointsDeducted;
            switch (decision.outcome()) {
                case CORRECT -> {
                    correctCount++;
                    rawPointsAwarded = itemPointWeight.setScale(2, RoundingMode.HALF_UP);
                    penaltyPointsDeducted = ZERO_2;
                }
                case WRONG -> {
                    wrongCount++;
                    rawPointsAwarded = ZERO_2;
                    penaltyPointsDeducted = formulaType == ScoringFormulaType.RAW_CORRECT_ONLY
                            ? ZERO_2
                            : itemPointWeight.multiply(wrongPenaltyRatio).setScale(2, RoundingMode.HALF_UP);
                }
                case BLANK -> {
                    blankCount++;
                    rawPointsAwarded = ZERO_2;
                    penaltyPointsDeducted = ZERO_2;
                }
                default -> throw new IllegalStateException("Unhandled outcome: " + decision.outcome());
            }

            BigDecimal netPointsContribution = rawPointsAwarded.subtract(penaltyPointsDeducted)
                    .setScale(2, RoundingMode.HALF_UP);
            weightedRawSum = weightedRawSum.add(rawPointsAwarded);
            weightedPenaltySum = weightedPenaltySum.add(penaltyPointsDeducted);

            int itemTimeSpent = Math.max(0, item.timeSpentSeconds());
            totalTimeSpentSeconds += itemTimeSpent;

            String topicCode = resolveTopicCode(item, question);
            String topicTitle = humanizeTopicCode(topicCode);
            int questionVersion = Math.max(1, question.examVersionNumber());
            String solutionExplanation = resolveSolutionExplanation(question);
            String evidenceSourceRef = resolveEvidenceSourceRef(question, questionVersion);

            List<ExamScratchpadAsset> scratchpads = item.scratchpadAssets() == null ? List.of() : item.scratchpadAssets();
            List<UUID> scratchpadAssetIds = scratchpads.stream().map(ExamScratchpadAsset::id).toList();
            F28ScratchpadTraceStatus f28Status = scratchpadAssetIds.isEmpty()
                    ? F28ScratchpadTraceStatus.NONE
                    : F28ScratchpadTraceStatus.ATTACHED_METADATA_ONLY;

            ExamSessionItemEvaluation itemEvaluation = new ExamSessionItemEvaluation(
                    UUID.randomUUID(),
                    scoreId,
                    session.id(),
                    session.userId(),
                    item.id(),
                    question.id(),
                    item.itemPosition(),
                    question.questionCode(),
                    topicCode,
                    topicTitle,
                    difficultyBand,
                    cognitiveLevel,
                    formatType,
                    decision.outcome(),
                    decision.normalizedSelectedOptionKey(),
                    decision.numericResponseValue(),
                    decision.correctOptionKey(),
                    decision.expectedNumericAnswer(),
                    decision.numericTolerance(),
                    rawPointsAwarded,
                    penaltyPointsDeducted,
                    netPointsContribution,
                    item.flaggedForReview(),
                    itemTimeSpent,
                    decision.distractorRationale(),
                    solutionExplanation,
                    evidenceSourceRef,
                    f28Status,
                    scratchpadAssetIds.size(),
                    scratchpadAssetIds,
                    questionVersion,
                    0,
                    false,
                    scoredAt
            );
            itemEvaluations.add(itemEvaluation);

            topicAccumulators
                    .computeIfAbsent(topicCode, k -> new TopicAccumulator(topicCode, topicTitle))
                    .record(decision.outcome(), rawPointsAwarded, penaltyPointsDeducted, itemTimeSpent);
            difficultyAccumulators
                    .computeIfAbsent(difficultyBand, DifficultyAccumulator::new)
                    .record(decision.outcome(), netPointsContribution, itemTimeSpent);
        }

        // Delegate aggregate formula validation & aggregate score computation to ScoringFormulaPolicy
        ScoringEvaluationResult aggregateResult = ScoringFormulaPolicy.evaluateAggregateForQuestionCount(
                definition,
                totalQuestions,
                correctCount,
                wrongCount,
                blankCount
        );

        BigDecimal minPossibleNetScore;
        BigDecimal maxPossibleNetScore;
        BigDecimal rawCorrectScore;
        BigDecimal wrongPenaltyDeduction;
        BigDecimal netScore;

        if (formulaType == ScoringFormulaType.WEIGHTED_SECTION_RUBRIC) {
            rawCorrectScore = weightedRawSum.setScale(2, RoundingMode.HALF_UP);
            wrongPenaltyDeduction = weightedPenaltySum.setScale(2, RoundingMode.HALF_UP);
            BigDecimal candidateMax = weightedMaxPossible.setScale(2, RoundingMode.HALF_UP);
            maxPossibleNetScore = candidateMax.max(ScoringFormulaPolicy.maxPossibleNetScore(formulaType, totalQuestions));
            minPossibleNetScore = wrongPenaltyRatio.compareTo(BigDecimal.ZERO) == 0
                    ? ZERO_2
                    : maxPossibleNetScore.multiply(wrongPenaltyRatio).negate().setScale(2, RoundingMode.HALF_UP);
            BigDecimal computedNet = rawCorrectScore.subtract(wrongPenaltyDeduction).setScale(2, RoundingMode.HALF_UP);
            netScore = computedNet.max(minPossibleNetScore).min(maxPossibleNetScore);
        } else {
            rawCorrectScore = BigDecimal.valueOf(correctCount).setScale(2, RoundingMode.HALF_UP);
            wrongPenaltyDeduction = formulaType == ScoringFormulaType.RAW_CORRECT_ONLY
                    ? ZERO_2
                    : wrongPenaltyRatio.multiply(BigDecimal.valueOf(wrongCount)).setScale(2, RoundingMode.HALF_UP);
            minPossibleNetScore = ScoringFormulaPolicy.minPossibleNetScore(formulaType, wrongPenaltyRatio, totalQuestions);
            maxPossibleNetScore = ScoringFormulaPolicy.maxPossibleNetScore(formulaType, totalQuestions);
            netScore = aggregateResult.rawNetScore()
                    .setScale(2, RoundingMode.HALF_UP)
                    .max(minPossibleNetScore)
                    .min(maxPossibleNetScore);
        }

        BigDecimal normalizedPercentageScore = ScoringFormulaPolicy.computeNormalizedPercentageScore(
                netScore,
                maxPossibleNetScore
        );
        BigDecimal accuracyPercentage = ScoringFormulaPolicy.computeAccuracyPercentage(correctCount, wrongCount);
        BigDecimal averageSecondsPerItem = BigDecimal.valueOf(totalTimeSpentSeconds)
                .divide(BigDecimal.valueOf(totalQuestions), 2, RoundingMode.HALF_UP);

        List<TopicScoreBreakdown> topicBreakdowns = topicAccumulators.values().stream()
                .sorted(Comparator.comparing(TopicAccumulator::topicCode))
                .map(acc -> acc.toBreakdown(formulaType, wrongPenaltyRatio))
                .toList();

        List<DifficultyScoreBreakdown> difficultyBreakdowns = new ArrayList<>();
        for (DifficultyBand band : DifficultyBand.values()) {
            DifficultyAccumulator acc = difficultyAccumulators.get(band);
            if (acc != null) {
                difficultyBreakdowns.add(acc.toBreakdown());
            }
        }

        return new ExamSessionScoreSnapshot(
                scoreId,
                session.id(),
                session.userId(),
                definition.id(),
                session.assemblyId(),
                session.examCode(),
                formulaType,
                wrongPenaltyRatio,
                totalQuestions,
                correctCount,
                wrongCount,
                blankCount,
                rawCorrectScore,
                wrongPenaltyDeduction,
                netScore,
                minPossibleNetScore,
                maxPossibleNetScore,
                normalizedPercentageScore,
                accuracyPercentage,
                totalTimeSpentSeconds,
                averageSecondsPerItem,
                topicBreakdowns,
                difficultyBreakdowns,
                itemEvaluations,
                scoreVersion,
                sessionSubmittedAt,
                scoredAt,
                lastRegradedAt,
                0,
                ZERO_6,
                false
        );
    }

    private EvaluationDecision evaluateItemResponse(
            ExamSessionItem item,
            QuestionBankItem question,
            QuestionFormatType formatType
    ) {
        return switch (formatType) {
            case SINGLE_CHOICE_MCQ -> evaluateSingleChoice(item, question);
            case MULTI_SELECT_MCQ -> evaluateMultiSelect(item, question);
            case NUMERIC_GRID_IN -> evaluateNumericGridIn(item, question);
        };
    }

    private EvaluationDecision evaluateSingleChoice(ExamSessionItem item, QuestionBankItem question) {
        List<QuestionOption> options = question.options() == null ? List.of() : question.options();
        String correctKey = options.stream()
                .filter(QuestionOption::isCorrect)
                .map(opt -> opt.optionKey().trim().toUpperCase(Locale.ROOT))
                .findFirst()
                .orElse(null);

        String rawSelected = item.selectedOptionKey();
        if (rawSelected == null || rawSelected.isBlank()) {
            return new EvaluationDecision(
                    ItemEvaluationOutcome.BLANK,
                    null,
                    null,
                    correctKey,
                    null,
                    null,
                    null
            );
        }

        String normalizedSelected = rawSelected.trim().toUpperCase(Locale.ROOT);
        boolean isCorrect = correctKey != null && correctKey.equals(normalizedSelected);
        String distractorRationale = null;
        if (!isCorrect) {
            distractorRationale = options.stream()
                    .filter(opt -> opt.optionKey().trim().toUpperCase(Locale.ROOT).equals(normalizedSelected))
                    .map(QuestionOption::distractorRationale)
                    .filter(r -> r != null && !r.isBlank())
                    .map(String::trim)
                    .findFirst()
                    .orElse(null);
        }

        return new EvaluationDecision(
                isCorrect ? ItemEvaluationOutcome.CORRECT : ItemEvaluationOutcome.WRONG,
                normalizedSelected,
                null,
                correctKey,
                null,
                null,
                distractorRationale
        );
    }

    private EvaluationDecision evaluateMultiSelect(ExamSessionItem item, QuestionBankItem question) {
        List<QuestionOption> options = question.options() == null ? List.of() : question.options();
        TreeSet<String> correctKeys = options.stream()
                .filter(QuestionOption::isCorrect)
                .map(opt -> opt.optionKey().trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(TreeSet::new));
        String canonicalCorrect = correctKeys.isEmpty() ? null : String.join(",", correctKeys);

        String rawSelected = item.selectedOptionKey();
        if (rawSelected == null || rawSelected.isBlank()) {
            return new EvaluationDecision(
                    ItemEvaluationOutcome.BLANK,
                    null,
                    null,
                    canonicalCorrect,
                    null,
                    null,
                    null
            );
        }

        TreeSet<String> selectedKeys = new TreeSet<>();
        for (String token : rawSelected.split(",")) {
            if (!token.isBlank()) {
                selectedKeys.add(token.trim().toUpperCase(Locale.ROOT));
            }
        }
        if (selectedKeys.isEmpty()) {
            return new EvaluationDecision(
                    ItemEvaluationOutcome.BLANK,
                    null,
                    null,
                    canonicalCorrect,
                    null,
                    null,
                    null
            );
        }

        String canonicalSelected = String.join(",", selectedKeys);
        boolean isCorrect = !correctKeys.isEmpty() && correctKeys.equals(selectedKeys);
        String distractorRationale = null;
        if (!isCorrect) {
            List<String> rationales = options.stream()
                    .filter(opt -> !opt.isCorrect())
                    .filter(opt -> selectedKeys.contains(opt.optionKey().trim().toUpperCase(Locale.ROOT)))
                    .map(QuestionOption::distractorRationale)
                    .filter(r -> r != null && !r.isBlank())
                    .map(String::trim)
                    .toList();
            if (!rationales.isEmpty()) {
                distractorRationale = String.join(" | ", rationales);
            }
        }

        return new EvaluationDecision(
                isCorrect ? ItemEvaluationOutcome.CORRECT : ItemEvaluationOutcome.WRONG,
                canonicalSelected,
                null,
                canonicalCorrect,
                null,
                null,
                distractorRationale
        );
    }

    private EvaluationDecision evaluateNumericGridIn(ExamSessionItem item, QuestionBankItem question) {
        BigDecimal expected = question.numericCorrectAnswer();
        BigDecimal tolerance = question.numericTolerance() == null
                ? BigDecimal.ZERO
                : question.numericTolerance().abs();

        BigDecimal submittedValue = item.numericResponseValue();
        if (submittedValue == null && item.selectedOptionKey() != null && !item.selectedOptionKey().isBlank()) {
            try {
                submittedValue = new BigDecimal(item.selectedOptionKey().trim());
            } catch (NumberFormatException ignored) {
                submittedValue = null;
            }
        }

        if (submittedValue == null) {
            return new EvaluationDecision(
                    ItemEvaluationOutcome.BLANK,
                    null,
                    null,
                    null,
                    expected,
                    tolerance,
                    null
            );
        }

        boolean isCorrect = expected != null
                && submittedValue.subtract(expected).abs().compareTo(tolerance) <= 0;

        String distractorRationale = isCorrect
                ? null
                : "Submitted numeric value differs from the deterministic expected answer beyond configured tolerance.";

        return new EvaluationDecision(
                isCorrect ? ItemEvaluationOutcome.CORRECT : ItemEvaluationOutcome.WRONG,
                null,
                submittedValue,
                null,
                expected,
                tolerance,
                distractorRationale
        );
    }

    private Map<String, BigDecimal> computeSectionItemPointMultipliers(
            ExamDefinition definition,
            List<ExamSessionItem> items
    ) {
        if (definition.scoringFormulaType() != ScoringFormulaType.WEIGHTED_SECTION_RUBRIC
                || definition.scoringParameters() == null
                || definition.scoringParameters().sectionWeights() == null
                || definition.scoringParameters().sectionWeights().isEmpty()) {
            return Map.of();
        }

        Map<String, BigDecimal> configuredWeights = new LinkedHashMap<>();
        for (SectionWeightEntry entry : definition.scoringParameters().sectionWeights()) {
            if (entry.sectionCode() != null && entry.weight() > 0.0) {
                configuredWeights.put(entry.sectionCode(), BigDecimal.valueOf(entry.weight()));
            }
        }
        if (configuredWeights.isEmpty()) {
            return Map.of();
        }

        Map<String, Integer> itemCountsBySection = new LinkedHashMap<>();
        for (ExamSessionItem item : items) {
            String sectionCode = item.sectionCode() == null ? "" : item.sectionCode();
            itemCountsBySection.merge(sectionCode, 1, Integer::sum);
        }

        BigDecimal presentWeightSum = BigDecimal.ZERO;
        for (String presentSection : itemCountsBySection.keySet()) {
            presentWeightSum = presentWeightSum.add(configuredWeights.getOrDefault(presentSection, BigDecimal.ZERO));
        }
        if (presentWeightSum.compareTo(BigDecimal.ZERO) <= 0) {
            return Map.of();
        }

        int totalQuestions = items.size();
        Map<String, BigDecimal> multipliers = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : itemCountsBySection.entrySet()) {
            BigDecimal weight = configuredWeights.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            if (weight.compareTo(BigDecimal.ZERO) <= 0 || entry.getValue() <= 0) {
                multipliers.put(entry.getKey(), ZERO_2);
            } else {
                BigDecimal normalizedWeight = weight.divide(presentWeightSum, 6, RoundingMode.HALF_UP);
                BigDecimal perItem = normalizedWeight
                        .multiply(BigDecimal.valueOf(totalQuestions))
                        .divide(BigDecimal.valueOf(entry.getValue()), 4, RoundingMode.HALF_UP);
                multipliers.put(entry.getKey(), perItem);
            }
        }
        return multipliers;
    }

    private static String resolveTopicCode(ExamSessionItem item, QuestionBankItem question) {
        if (question.topicCode() != null && !question.topicCode().isBlank()) {
            return question.topicCode().trim();
        }
        if (item.topicCode() != null && !item.topicCode().isBlank()) {
            return item.topicCode().trim();
        }
        return "GENERAL";
    }

    private static String humanizeTopicCode(String topicCode) {
        String[] segments = topicCode.split("\\.");
        String leaf = segments[segments.length - 1];
        String[] words = leaf.split("[_\\-]+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                sb.append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        String result = sb.isEmpty() ? topicCode : sb.toString();
        return result.length() > 160 ? result.substring(0, 160) : result;
    }

    private static String resolveSolutionExplanation(QuestionBankItem question) {
        if (question.solutionExplanationMarkdown() != null && !question.solutionExplanationMarkdown().isBlank()) {
            return question.solutionExplanationMarkdown().trim();
        }
        return "Verified deterministic solution explanation for " + question.questionCode() + ".";
    }

    private static String resolveEvidenceSourceRef(QuestionBankItem question, int questionVersion) {
        String raw = null;
        if (question.provenance() != null
                && question.provenance().sourceReference() != null
                && !question.provenance().sourceReference().isBlank()) {
            raw = question.provenance().sourceReference().trim();
        } else {
            raw = "question_bank:" + question.questionCode() + ":v" + questionVersion;
        }
        return raw.length() > 160 ? raw.substring(0, 160) : raw;
    }

    private record EvaluationDecision(
            ItemEvaluationOutcome outcome,
            String normalizedSelectedOptionKey,
            BigDecimal numericResponseValue,
            String correctOptionKey,
            BigDecimal expectedNumericAnswer,
            BigDecimal numericTolerance,
            String distractorRationale
    ) {
    }

    private static final class TopicAccumulator {
        private final String topicCode;
        private final String topicTitle;
        private int total;
        private int correct;
        private int wrong;
        private int blank;
        private int totalTimeSeconds;
        private BigDecimal rawSum = BigDecimal.ZERO;
        private BigDecimal penaltySum = BigDecimal.ZERO;

        private TopicAccumulator(String topicCode, String topicTitle) {
            this.topicCode = topicCode;
            this.topicTitle = topicTitle;
        }

        private String topicCode() {
            return topicCode;
        }

        private void record(
                ItemEvaluationOutcome outcome,
                BigDecimal rawPoints,
                BigDecimal penaltyPoints,
                int timeSpentSeconds
        ) {
            total++;
            switch (outcome) {
                case CORRECT -> correct++;
                case WRONG -> wrong++;
                case BLANK -> blank++;
            }
            totalTimeSeconds += timeSpentSeconds;
            rawSum = rawSum.add(rawPoints);
            penaltySum = penaltySum.add(penaltyPoints);
        }

        private TopicScoreBreakdown toBreakdown(ScoringFormulaType formulaType, BigDecimal wrongPenaltyRatio) {
            BigDecimal raw;
            BigDecimal penalty;
            if (formulaType == ScoringFormulaType.WEIGHTED_SECTION_RUBRIC) {
                raw = rawSum.setScale(2, RoundingMode.HALF_UP);
                penalty = penaltySum.setScale(2, RoundingMode.HALF_UP);
            } else {
                raw = BigDecimal.valueOf(correct).setScale(2, RoundingMode.HALF_UP);
                penalty = formulaType == ScoringFormulaType.RAW_CORRECT_ONLY
                        ? ZERO_2
                        : wrongPenaltyRatio.multiply(BigDecimal.valueOf(wrong)).setScale(2, RoundingMode.HALF_UP);
            }
            BigDecimal net = raw.subtract(penalty).setScale(2, RoundingMode.HALF_UP);
            BigDecimal accuracy = ScoringFormulaPolicy.computeAccuracyPercentage(correct, wrong);
            BigDecimal avgSec = BigDecimal.valueOf(totalTimeSeconds)
                    .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
            return new TopicScoreBreakdown(
                    topicCode,
                    topicTitle,
                    total,
                    correct,
                    wrong,
                    blank,
                    raw,
                    penalty,
                    net,
                    accuracy,
                    totalTimeSeconds,
                    avgSec
            );
        }
    }

    private static final class DifficultyAccumulator {
        private final DifficultyBand difficultyBand;
        private int total;
        private int correct;
        private int wrong;
        private int blank;
        private int totalTimeSeconds;
        private BigDecimal netSum = BigDecimal.ZERO;

        private DifficultyAccumulator(DifficultyBand difficultyBand) {
            this.difficultyBand = difficultyBand;
        }

        private void record(ItemEvaluationOutcome outcome, BigDecimal netPoints, int timeSpentSeconds) {
            total++;
            switch (outcome) {
                case CORRECT -> correct++;
                case WRONG -> wrong++;
                case BLANK -> blank++;
            }
            totalTimeSeconds += timeSpentSeconds;
            netSum = netSum.add(netPoints);
        }

        private DifficultyScoreBreakdown toBreakdown() {
            BigDecimal accuracy = ScoringFormulaPolicy.computeAccuracyPercentage(correct, wrong);
            BigDecimal avgSec = BigDecimal.valueOf(totalTimeSeconds)
                    .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
            return new DifficultyScoreBreakdown(
                    difficultyBand,
                    total,
                    correct,
                    wrong,
                    blank,
                    netSum.setScale(2, RoundingMode.HALF_UP),
                    accuracy,
                    totalTimeSeconds,
                    avgSec
            );
        }
    }
}
