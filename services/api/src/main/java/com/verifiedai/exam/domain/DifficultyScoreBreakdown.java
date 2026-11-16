package com.verifiedai.exam.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record DifficultyScoreBreakdown(
        DifficultyBand difficultyBand,
        int totalQuestions,
        int correctCount,
        int wrongCount,
        int blankCount,
        BigDecimal netScore,
        BigDecimal accuracyPercentage,
        int totalTimeSpentSeconds,
        BigDecimal averageSecondsPerItem
) {
    public DifficultyScoreBreakdown {
        Objects.requireNonNull(difficultyBand, "difficultyBand is required");
        if (totalQuestions < 1 || correctCount < 0 || wrongCount < 0 || blankCount < 0
                || correctCount + wrongCount + blankCount != totalQuestions) {
            throw new IllegalArgumentException("Difficulty breakdown counts must be non-negative and sum to totalQuestions");
        }
        if (totalTimeSpentSeconds < 0) {
            throw new IllegalArgumentException("totalTimeSpentSeconds must be non-negative");
        }
        netScore = scale2(Objects.requireNonNull(netScore, "netScore is required"));
        accuracyPercentage = scale2(Objects.requireNonNull(accuracyPercentage, "accuracyPercentage is required"));
        averageSecondsPerItem = scale2(Objects.requireNonNull(averageSecondsPerItem, "averageSecondsPerItem is required"));
    }

    private static BigDecimal scale2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
