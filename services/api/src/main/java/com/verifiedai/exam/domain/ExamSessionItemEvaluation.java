package com.verifiedai.exam.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ExamSessionItemEvaluation(
        UUID evaluationId,
        UUID scoreId,
        UUID sessionId,
        UUID userId,
        UUID sessionItemId,
        UUID questionItemId,
        int positionIndex,
        String questionCode,
        String topicCode,
        String topicTitle,
        DifficultyBand difficultyBand,
        CognitiveLevel cognitiveLevel,
        QuestionFormatType formatType,
        ItemEvaluationOutcome evaluationOutcome,
        String selectedOptionKey,
        BigDecimal numericResponseValue,
        String correctOptionKey,
        BigDecimal expectedNumericAnswer,
        BigDecimal numericTolerance,
        BigDecimal rawPointsAwarded,
        BigDecimal penaltyPointsDeducted,
        BigDecimal netPointsContribution,
        boolean flaggedForReview,
        int timeSpentSeconds,
        String selectedOptionDistractorRationale,
        String solutionExplanationMarkdown,
        String evidenceSourceRef,
        F28ScratchpadTraceStatus f28ScratchpadStatus,
        int scratchpadAssetCount,
        List<UUID> scratchpadAssetIds,
        int questionVersion,
        int llmTokensUsed,
        boolean trainingEligible,
        Instant evaluatedAt
) {
    public ExamSessionItemEvaluation {
        Objects.requireNonNull(evaluationId, "evaluationId is required");
        Objects.requireNonNull(scoreId, "scoreId is required");
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(sessionItemId, "sessionItemId is required");
        Objects.requireNonNull(questionItemId, "questionItemId is required");
        if (positionIndex < 1 || positionIndex > 200) {
            throw new IllegalArgumentException("positionIndex must be between 1 and 200");
        }
        if (questionCode == null || questionCode.isBlank()) {
            throw new IllegalArgumentException("questionCode must not be blank");
        }
        if (topicCode == null || topicCode.isBlank()) {
            throw new IllegalArgumentException("topicCode must not be blank");
        }
        if (topicTitle == null || topicTitle.isBlank()) {
            throw new IllegalArgumentException("topicTitle must not be blank");
        }
        Objects.requireNonNull(difficultyBand, "difficultyBand is required");
        Objects.requireNonNull(cognitiveLevel, "cognitiveLevel is required");
        Objects.requireNonNull(formatType, "formatType is required");
        Objects.requireNonNull(evaluationOutcome, "evaluationOutcome is required");
        if (timeSpentSeconds < 0) {
            throw new IllegalArgumentException("timeSpentSeconds must be non-negative");
        }
        if (solutionExplanationMarkdown == null || solutionExplanationMarkdown.isBlank()) {
            throw new IllegalArgumentException("solutionExplanationMarkdown must not be blank");
        }
        if (evidenceSourceRef == null || evidenceSourceRef.isBlank()) {
            throw new IllegalArgumentException("evidenceSourceRef must not be blank");
        }
        Objects.requireNonNull(f28ScratchpadStatus, "f28ScratchpadStatus is required");
        scratchpadAssetIds = scratchpadAssetIds == null ? List.of() : List.copyOf(scratchpadAssetIds);
        if (scratchpadAssetCount < 0 || scratchpadAssetCount != scratchpadAssetIds.size()) {
            throw new IllegalArgumentException("scratchpadAssetCount must match scratchpadAssetIds.size()");
        }
        if (questionVersion < 1) {
            throw new IllegalArgumentException("questionVersion must be >= 1");
        }
        if (llmTokensUsed != 0) {
            throw new IllegalArgumentException("llmTokensUsed must be 0 per INV-EXAM-004");
        }
        if (trainingEligible) {
            throw new IllegalArgumentException("trainingEligible must be false per ADR-006");
        }
        Objects.requireNonNull(evaluatedAt, "evaluatedAt is required");

        questionCode = questionCode.trim();
        topicCode = topicCode.trim();
        topicTitle = topicTitle.trim();
        selectedOptionKey = normalizeNullable(selectedOptionKey);
        correctOptionKey = normalizeNullable(correctOptionKey);
        selectedOptionDistractorRationale = normalizeNullable(selectedOptionDistractorRationale);
        solutionExplanationMarkdown = solutionExplanationMarkdown.trim();
        evidenceSourceRef = evidenceSourceRef.trim();

        numericResponseValue = numericResponseValue == null ? null : numericResponseValue.setScale(4, RoundingMode.HALF_UP);
        expectedNumericAnswer = expectedNumericAnswer == null ? null : expectedNumericAnswer.setScale(4, RoundingMode.HALF_UP);
        numericTolerance = numericTolerance == null ? null : numericTolerance.setScale(6, RoundingMode.HALF_UP);
        rawPointsAwarded = Objects.requireNonNull(rawPointsAwarded, "rawPointsAwarded is required")
                .setScale(2, RoundingMode.HALF_UP);
        penaltyPointsDeducted = Objects.requireNonNull(penaltyPointsDeducted, "penaltyPointsDeducted is required")
                .setScale(2, RoundingMode.HALF_UP);
        if (penaltyPointsDeducted.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("penaltyPointsDeducted must be non-negative");
        }
        netPointsContribution = Objects.requireNonNull(netPointsContribution, "netPointsContribution is required")
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
