package com.verifiedai.studyplan.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.studyplan.domain.RecommendationRationaleCode;
import com.verifiedai.studyplan.domain.RecommendationType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class NextBestActionRecommendationEngine {

    public static final BigDecimal WEIGHT_MISTAKE = new BigDecimal("0.45");
    public static final BigDecimal WEIGHT_RETENTION = new BigDecimal("0.30");
    public static final BigDecimal WEIGHT_ROI = new BigDecimal("0.25");
    public static final BigDecimal RETENTION_THRESHOLD = new BigDecimal("0.75");
    public static final BigDecimal PREREQUISITE_MASTERY_THRESHOLD = new BigDecimal("0.50");
    public static final BigDecimal MISTAKE_REPAIR_TRIGGER_THRESHOLD = new BigDecimal("35.0000");

    public static final BigDecimal HUNDRED = new BigDecimal("100.0000");
    public static final BigDecimal ZERO = new BigDecimal("0.0000");
    private static final BigDecimal MIN_RETENTION = new BigDecimal("0.05");
    private static final BigDecimal MAX_RETENTION = new BigDecimal("0.99");

    public static final Comparator<ScoredRecommendation> DETERMINISTIC_COMPARATOR = Comparator
            .comparing(ScoredRecommendation::priorityScore, Comparator.reverseOrder())
            .thenComparingInt(r -> r.recommendationType().tieBreakOrder())
            .thenComparing(ScoredRecommendation::targetSkillCode);

    private final ObjectMapper objectMapper;

    public NextBestActionRecommendationEngine(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
    }

    public NextBestActionRecommendationEngine() {
        this(new ObjectMapper());
    }

    public ScoredRecommendation evaluateCandidate(SkillCandidateSignal signal) {
        Objects.requireNonNull(signal, "signal cannot be null");

        BigDecimal clampedMistakePropensity = signal.mistakePropensity().max(ZERO).min(HUNDRED);
        BigDecimal clampedRetention = signal.currentRetention().max(MIN_RETENTION).min(MAX_RETENTION);
        BigDecimal clampedIndependentMastery = signal.independentMastery().max(ZERO).min(BigDecimal.ONE);
        BigDecimal clampedExamWeight = signal.examWeight().max(ZERO).min(BigDecimal.ONE);

        // INV-COACH-001: Component calculation
        BigDecimal mistakeComponent = WEIGHT_MISTAKE.multiply(clampedMistakePropensity, MathContext.DECIMAL64);
        BigDecimal retentionGap = RETENTION_THRESHOLD.subtract(clampedRetention, MathContext.DECIMAL64).max(BigDecimal.ZERO);
        BigDecimal retentionComponent = WEIGHT_RETENTION.multiply(retentionGap, MathContext.DECIMAL64).multiply(HUNDRED, MathContext.DECIMAL64);
        BigDecimal roiRaw = clampedExamWeight
                .multiply(BigDecimal.ONE.subtract(clampedIndependentMastery, MathContext.DECIMAL64), MathContext.DECIMAL64)
                .multiply(HUNDRED, MathContext.DECIMAL64);
        BigDecimal roiComponent = WEIGHT_ROI.multiply(roiRaw, MathContext.DECIMAL64);

        BigDecimal totalScore = mistakeComponent
                .add(retentionComponent, MathContext.DECIMAL64)
                .add(roiComponent, MathContext.DECIMAL64)
                .min(HUNDRED)
                .max(ZERO)
                .setScale(4, RoundingMode.HALF_UP);

        // INV-COACH-002: Prerequisite Bottleneck Override check
        if (signal.weakestPrerequisiteSkillCode() != null
                && signal.weakestPrerequisiteMastery() != null
                && signal.weakestPrerequisiteMastery().compareTo(PREREQUISITE_MASTERY_THRESHOLD) < 0) {

            String targetSkillCode = signal.weakestPrerequisiteSkillCode();
            RecommendationType recommendationType = RecommendationType.PREREQUISITE_REPAIR;
            RecommendationRationaleCode rationaleCode = RecommendationRationaleCode.PREREQUISITE_REPAIR;

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("prerequisiteSkillCode", signal.weakestPrerequisiteSkillCode());
            params.put("blockedSkillCode", signal.skillCode());
            params.put("currentMastery", signal.weakestPrerequisiteMastery().setScale(4, RoundingMode.HALF_UP).doubleValue());
            params.put("requiredMasteryThreshold", PREREQUISITE_MASTERY_THRESHOLD.setScale(4, RoundingMode.HALF_UP).doubleValue());

            return new ScoredRecommendation(
                    targetSkillCode,
                    null,
                    recommendationType,
                    totalScore,
                    rationaleCode,
                    params,
                    toJson(params)
            );
        }

        // Dominant Rationale Selection
        RecommendationType recommendationType;
        RecommendationRationaleCode rationaleCode;
        String targetMistakeCode = null;
        Map<String, Object> params = new LinkedHashMap<>();

        boolean mistakeTriggered = clampedMistakePropensity.compareTo(MISTAKE_REPAIR_TRIGGER_THRESHOLD) >= 0;
        boolean mistakeDominant = mistakeTriggered && (
                mistakeComponent.compareTo(retentionComponent) >= 0
                || signal.dominantMistakeCode() != null
        );

        if (mistakeDominant) {
            recommendationType = RecommendationType.MISTAKE_REPAIR;
            rationaleCode = RecommendationRationaleCode.ACTIVE_RECURRING_TRAP;
            targetMistakeCode = signal.dominantMistakeCode() != null ? signal.dominantMistakeCode() : "GENERAL_TRAP";

            params.put("skillCode", signal.skillCode());
            params.put("mistakeCode", targetMistakeCode);
            params.put("mistakePropensity", clampedMistakePropensity.setScale(4, RoundingMode.HALF_UP).doubleValue());
            params.put("unresolvedTrapCount", signal.unresolvedTrapCount());
        } else if (clampedRetention.compareTo(RETENTION_THRESHOLD) < 0 && retentionComponent.compareTo(roiComponent) >= 0) {
            recommendationType = RecommendationType.SPACED_RETENTION;
            rationaleCode = RecommendationRationaleCode.RETENTION_HALF_LIFE_DUE;

            params.put("skillCode", signal.skillCode());
            params.put("currentRetention", clampedRetention.setScale(4, RoundingMode.HALF_UP).doubleValue());
            params.put("retentionThreshold", RETENTION_THRESHOLD.setScale(4, RoundingMode.HALF_UP).doubleValue());
            params.put("halfLifeDays", signal.halfLifeDays().setScale(2, RoundingMode.HALF_UP).doubleValue());
        } else {
            recommendationType = RecommendationType.HIGH_ROI_TOPIC;
            rationaleCode = RecommendationRationaleCode.HIGH_EXAM_NET_ROI;

            params.put("skillCode", signal.skillCode());
            params.put("examCode", signal.examCode());
            params.put("examWeight", clampedExamWeight.setScale(4, RoundingMode.HALF_UP).doubleValue());
            params.put("estimatedNetGain", roiRaw.divide(HUNDRED, MathContext.DECIMAL64).setScale(4, RoundingMode.HALF_UP).doubleValue());
            params.put("opportunityAreaScore", BigDecimal.ONE.subtract(clampedIndependentMastery, MathContext.DECIMAL64)
                    .multiply(HUNDRED, MathContext.DECIMAL64)
                    .setScale(4, RoundingMode.HALF_UP).doubleValue());
        }

        return new ScoredRecommendation(
                signal.skillCode(),
                targetMistakeCode,
                recommendationType,
                totalScore,
                rationaleCode,
                params,
                toJson(params)
        );
    }

    public List<ScoredRecommendation> rankCandidates(List<SkillCandidateSignal> signals, int limit) {
        if (signals == null || signals.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. Evaluate all candidates
        List<ScoredRecommendation> scoredList = signals.stream()
                .map(this::evaluateCandidate)
                .toList();

        // 2. Deduplication by targetSkillCode: retain the highest priority candidate
        Map<String, ScoredRecommendation> bestBySkill = new HashMap<>();
        for (ScoredRecommendation item : scoredList) {
            bestBySkill.merge(item.targetSkillCode(), item, (existing, replacement) -> {
                if (replacement.priorityScore().compareTo(existing.priorityScore()) > 0) {
                    return replacement;
                } else if (replacement.priorityScore().compareTo(existing.priorityScore()) == 0) {
                    return replacement.recommendationType().tieBreakOrder() < existing.recommendationType().tieBreakOrder()
                            ? replacement : existing;
                }
                return existing;
            });
        }

        // 3. Deterministic Sorting (INV-COACH-003)
        List<ScoredRecommendation> sorted = new ArrayList<>(bestBySkill.values());
        sorted.sort(DETERMINISTIC_COMPARATOR);

        // 4. Limit results
        if (limit > 0 && sorted.size() > limit) {
            return sorted.subList(0, limit);
        }
        return Collections.unmodifiableList(sorted);
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize rationale params to JSON", e);
        }
    }
}
