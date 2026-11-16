package com.verifiedai.exam.domain;

import com.verifiedai.exam.domain.ExamModels.ScoringFormulaType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ExamSessionScoreSnapshot(
        UUID scoreId,
        UUID sessionId,
        UUID userId,
        UUID examDefinitionId,
        UUID assembledAssessmentId,
        String examCode,
        ScoringFormulaType scoringFormulaType,
        BigDecimal wrongPenaltyRatio,
        int totalQuestions,
        int correctCount,
        int wrongCount,
        int blankCount,
        BigDecimal rawCorrectScore,
        BigDecimal wrongPenaltyDeduction,
        BigDecimal netScore,
        BigDecimal minPossibleNetScore,
        BigDecimal maxPossibleNetScore,
        BigDecimal normalizedPercentageScore,
        BigDecimal accuracyPercentage,
        int totalTimeSpentSeconds,
        BigDecimal averageSecondsPerItem,
        List<TopicScoreBreakdown> topicBreakdowns,
        List<DifficultyScoreBreakdown> difficultyBreakdowns,
        List<ExamSessionItemEvaluation> itemEvaluations,
        int scoreVersion,
        Instant sessionSubmittedAt,
        Instant scoredAt,
        Instant lastRegradedAt,
        int llmTokensUsed,
        BigDecimal inferenceCostUsd,
        boolean trainingEligible
) {
    public ExamSessionScoreSnapshot {
        Objects.requireNonNull(scoreId, "scoreId is required");
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(examDefinitionId, "examDefinitionId is required");
        Objects.requireNonNull(assembledAssessmentId, "assembledAssessmentId is required");
        if (examCode == null || examCode.isBlank()) {
            throw new IllegalArgumentException("examCode must not be blank");
        }
        Objects.requireNonNull(scoringFormulaType, "scoringFormulaType is required");
        wrongPenaltyRatio = Objects.requireNonNull(wrongPenaltyRatio, "wrongPenaltyRatio is required")
                .setScale(4, RoundingMode.HALF_UP);
        if (wrongPenaltyRatio.compareTo(BigDecimal.ZERO) < 0 || wrongPenaltyRatio.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("wrongPenaltyRatio must be between 0.0000 and 1.0000");
        }
        if (totalQuestions < 1 || totalQuestions > 200) {
            throw new IllegalArgumentException("totalQuestions must be between 1 and 200");
        }
        if (correctCount < 0 || wrongCount < 0 || blankCount < 0
                || correctCount + wrongCount + blankCount != totalQuestions) {
            throw new IllegalArgumentException("Response counts must be non-negative and sum to totalQuestions");
        }
        rawCorrectScore = Objects.requireNonNull(rawCorrectScore, "rawCorrectScore is required")
                .setScale(2, RoundingMode.HALF_UP);
        wrongPenaltyDeduction = Objects.requireNonNull(wrongPenaltyDeduction, "wrongPenaltyDeduction is required")
                .setScale(2, RoundingMode.HALF_UP);
        if (wrongPenaltyDeduction.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("wrongPenaltyDeduction must be non-negative");
        }
        netScore = Objects.requireNonNull(netScore, "netScore is required").setScale(2, RoundingMode.HALF_UP);
        minPossibleNetScore = Objects.requireNonNull(minPossibleNetScore, "minPossibleNetScore is required")
                .setScale(2, RoundingMode.HALF_UP);
        maxPossibleNetScore = Objects.requireNonNull(maxPossibleNetScore, "maxPossibleNetScore is required")
                .setScale(2, RoundingMode.HALF_UP);
        if (netScore.compareTo(minPossibleNetScore) < 0 || netScore.compareTo(maxPossibleNetScore) > 0) {
            throw new IllegalArgumentException("netScore must fall within [minPossibleNetScore, maxPossibleNetScore]");
        }
        normalizedPercentageScore = Objects.requireNonNull(normalizedPercentageScore, "normalizedPercentageScore is required")
                .setScale(2, RoundingMode.HALF_UP);
        if (normalizedPercentageScore.compareTo(BigDecimal.ZERO) < 0
                || normalizedPercentageScore.compareTo(new BigDecimal("100.00")) > 0) {
            throw new IllegalArgumentException("normalizedPercentageScore must be in [0.00, 100.00]");
        }
        accuracyPercentage = Objects.requireNonNull(accuracyPercentage, "accuracyPercentage is required")
                .setScale(2, RoundingMode.HALF_UP);
        if (accuracyPercentage.compareTo(BigDecimal.ZERO) < 0
                || accuracyPercentage.compareTo(new BigDecimal("100.00")) > 0) {
            throw new IllegalArgumentException("accuracyPercentage must be in [0.00, 100.00]");
        }
        if (totalTimeSpentSeconds < 0) {
            throw new IllegalArgumentException("totalTimeSpentSeconds must be non-negative");
        }
        averageSecondsPerItem = Objects.requireNonNull(averageSecondsPerItem, "averageSecondsPerItem is required")
                .setScale(2, RoundingMode.HALF_UP);
        topicBreakdowns = topicBreakdowns == null ? List.of() : List.copyOf(topicBreakdowns);
        difficultyBreakdowns = difficultyBreakdowns == null ? List.of() : List.copyOf(difficultyBreakdowns);
        itemEvaluations = itemEvaluations == null ? List.of() : List.copyOf(itemEvaluations);
        if (scoreVersion < 1) {
            throw new IllegalArgumentException("scoreVersion must be >= 1");
        }
        Objects.requireNonNull(sessionSubmittedAt, "sessionSubmittedAt is required");
        Objects.requireNonNull(scoredAt, "scoredAt is required");
        if (llmTokensUsed != 0) {
            throw new IllegalArgumentException("llmTokensUsed must be 0 per INV-EXAM-004");
        }
        inferenceCostUsd = Objects.requireNonNull(inferenceCostUsd, "inferenceCostUsd is required")
                .setScale(6, RoundingMode.HALF_UP);
        if (inferenceCostUsd.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException("inferenceCostUsd must be 0.000000 per INV-EXAM-004");
        }
        if (trainingEligible) {
            throw new IllegalArgumentException("trainingEligible must be false per ADR-006");
        }
        examCode = examCode.trim();
    }

    public ExamSessionScoreSnapshot forRegrade(int newScoreVersion, Instant originalScoredAt, Instant regradedAt) {
        return new ExamSessionScoreSnapshot(
                scoreId,
                sessionId,
                userId,
                examDefinitionId,
                assembledAssessmentId,
                examCode,
                scoringFormulaType,
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
                newScoreVersion,
                sessionSubmittedAt,
                originalScoredAt,
                regradedAt,
                llmTokensUsed,
                inferenceCostUsd,
                trainingEligible
        );
    }
}
