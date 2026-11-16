package com.verifiedai.exam.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record TopicScoreBreakdown(
        String topicCode,
        String topicTitle,
        int totalQuestions,
        int correctCount,
        int wrongCount,
        int blankCount,
        BigDecimal rawCorrectScore,
        BigDecimal wrongPenaltyDeduction,
        BigDecimal netScore,
        BigDecimal accuracyPercentage,
        int totalTimeSpentSeconds,
        BigDecimal averageSecondsPerItem
) {
    public TopicScoreBreakdown {
        if (topicCode == null || topicCode.isBlank()) {
            throw new IllegalArgumentException("topicCode must not be blank");
        }
        if (topicTitle == null || topicTitle.isBlank()) {
            throw new IllegalArgumentException("topicTitle must not be blank");
        }
        if (totalQuestions < 1 || correctCount < 0 || wrongCount < 0 || blankCount < 0
                || correctCount + wrongCount + blankCount != totalQuestions) {
            throw new IllegalArgumentException("Topic breakdown counts must be non-negative and sum to totalQuestions");
        }
        if (totalTimeSpentSeconds < 0) {
            throw new IllegalArgumentException("totalTimeSpentSeconds must be non-negative");
        }
        topicCode = topicCode.trim();
        topicTitle = topicTitle.trim();
        rawCorrectScore = scale2(Objects.requireNonNull(rawCorrectScore, "rawCorrectScore is required"));
        wrongPenaltyDeduction = scale2(Objects.requireNonNull(wrongPenaltyDeduction, "wrongPenaltyDeduction is required"));
        netScore = scale2(Objects.requireNonNull(netScore, "netScore is required"));
        accuracyPercentage = scale2(Objects.requireNonNull(accuracyPercentage, "accuracyPercentage is required"));
        averageSecondsPerItem = scale2(Objects.requireNonNull(averageSecondsPerItem, "averageSecondsPerItem is required"));
    }

    private static BigDecimal scale2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
