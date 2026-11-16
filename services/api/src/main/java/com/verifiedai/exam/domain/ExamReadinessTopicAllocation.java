package com.verifiedai.exam.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

/**
 * Auditable high-ROI study topic allocation ranked by expected net gain per study hour.
 */
public record ExamReadinessTopicAllocation(
        UUID allocationId,
        String sectionCode,
        String skillCode,
        String topicTitleKey,
        BigDecimal blueprintQuestionWeight,
        BigDecimal currentMastery,
        BigDecimal retentionProbability,
        BigDecimal expectedNetGainPerHour,
        int recommendedStudyMinutes,
        int priorityRank,
        ExamReadinessRationaleCode rationaleCode,
        boolean trainingEligible
) {
    public ExamReadinessTopicAllocation {
        Objects.requireNonNull(allocationId, "allocationId is required");
        if (sectionCode == null || sectionCode.isBlank()) {
            throw new IllegalArgumentException("sectionCode must not be blank");
        }
        if (skillCode == null || skillCode.isBlank()) {
            throw new IllegalArgumentException("skillCode must not be blank");
        }
        if (topicTitleKey == null || topicTitleKey.isBlank()) {
            throw new IllegalArgumentException("topicTitleKey must not be blank");
        }
        blueprintQuestionWeight = Objects.requireNonNull(blueprintQuestionWeight, "blueprintQuestionWeight is required")
                .setScale(2, RoundingMode.HALF_UP);
        if (blueprintQuestionWeight.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("blueprintQuestionWeight must be non-negative");
        }
        currentMastery = normalizeUnit(currentMastery, "currentMastery");
        retentionProbability = normalizeUnit(retentionProbability, "retentionProbability");
        expectedNetGainPerHour = Objects.requireNonNull(expectedNetGainPerHour, "expectedNetGainPerHour is required")
                .setScale(3, RoundingMode.HALF_UP);
        if (expectedNetGainPerHour.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("expectedNetGainPerHour must be non-negative");
        }
        if (recommendedStudyMinutes <= 0) {
            throw new IllegalArgumentException("recommendedStudyMinutes must be > 0");
        }
        if (priorityRank < 1) {
            throw new IllegalArgumentException("priorityRank must be >= 1");
        }
        Objects.requireNonNull(rationaleCode, "rationaleCode is required");
        if (trainingEligible) {
            throw new IllegalArgumentException("trainingEligible must be false per ADR-006");
        }
        sectionCode = sectionCode.trim();
        skillCode = skillCode.trim();
        topicTitleKey = topicTitleKey.trim();
    }

    private static BigDecimal normalizeUnit(BigDecimal value, String field) {
        BigDecimal scaled = Objects.requireNonNull(value, field + " is required")
                .setScale(4, RoundingMode.HALF_UP);
        if (scaled.compareTo(BigDecimal.ZERO) < 0 || scaled.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(field + " must be in [0.0000, 1.0000]");
        }
        return scaled;
    }
}
