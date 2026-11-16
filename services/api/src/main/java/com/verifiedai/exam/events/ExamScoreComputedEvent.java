package com.verifiedai.exam.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ExamScoreComputedEvent(
        UUID eventId,
        UUID scoreId,
        UUID sessionId,
        UUID userId,
        UUID examDefinitionId,
        UUID assembledAssessmentId,
        String examCode,
        int scoreVersion,
        boolean regrade,
        String scoringFormulaType,
        int totalQuestions,
        int correctCount,
        int wrongCount,
        int blankCount,
        BigDecimal netScore,
        BigDecimal normalizedPercentageScore,
        BigDecimal accuracyPercentage,
        List<TopicScoreSummary> topicBreakdowns,
        int llmTokensUsed,
        boolean trainingEligible,
        Instant occurredAt
) {
    public record TopicScoreSummary(
            String topicCode,
            String topicTitle,
            int totalQuestions,
            int correctCount,
            int wrongCount,
            int blankCount,
            BigDecimal netScore,
            BigDecimal accuracyPercentage,
            int totalTimeSpentSeconds
    ) {
        public TopicScoreSummary {
            if (topicCode == null || topicCode.isBlank()) {
                throw new IllegalArgumentException("topicCode must not be blank");
            }
            if (topicTitle == null || topicTitle.isBlank()) {
                throw new IllegalArgumentException("topicTitle must not be blank");
            }
            Objects.requireNonNull(netScore, "netScore is required");
            Objects.requireNonNull(accuracyPercentage, "accuracyPercentage is required");
        }
    }

    public ExamScoreComputedEvent {
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(scoreId, "scoreId is required");
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(examDefinitionId, "examDefinitionId is required");
        Objects.requireNonNull(assembledAssessmentId, "assembledAssessmentId is required");
        if (examCode == null || examCode.isBlank()) {
            throw new IllegalArgumentException("examCode must not be blank");
        }
        if (scoreVersion < 1) {
            throw new IllegalArgumentException("scoreVersion must be >= 1");
        }
        if (scoringFormulaType == null || scoringFormulaType.isBlank()) {
            throw new IllegalArgumentException("scoringFormulaType must not be blank");
        }
        if (totalQuestions < 1 || correctCount + wrongCount + blankCount != totalQuestions) {
            throw new IllegalArgumentException("Response counts must sum to totalQuestions");
        }
        Objects.requireNonNull(netScore, "netScore is required");
        Objects.requireNonNull(normalizedPercentageScore, "normalizedPercentageScore is required");
        Objects.requireNonNull(accuracyPercentage, "accuracyPercentage is required");
        topicBreakdowns = topicBreakdowns == null ? List.of() : List.copyOf(topicBreakdowns);
        if (llmTokensUsed != 0) {
            throw new IllegalArgumentException("llmTokensUsed must be 0 per INV-EXAM-004");
        }
        if (trainingEligible) {
            throw new IllegalArgumentException("trainingEligible must be false per ADR-006");
        }
        Objects.requireNonNull(occurredAt, "occurredAt is required");
    }
}
