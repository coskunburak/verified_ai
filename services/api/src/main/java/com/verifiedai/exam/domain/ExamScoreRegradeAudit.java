package com.verifiedai.exam.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record ExamScoreRegradeAudit(
        UUID regradeAuditId,
        UUID scoreId,
        UUID sessionId,
        UUID userId,
        int previousScoreVersion,
        int newScoreVersion,
        BigDecimal previousNetScore,
        BigDecimal newNetScore,
        int previousCorrectCount,
        int newCorrectCount,
        int previousWrongCount,
        int newWrongCount,
        int previousBlankCount,
        int newBlankCount,
        ScoreRegradeReasonCode reasonCode,
        String reasonDetail,
        String triggeredBy,
        Map<String, Object> deltaSummary,
        boolean trainingEligible,
        Instant regradedAt
) {
    public ExamScoreRegradeAudit {
        Objects.requireNonNull(regradeAuditId, "regradeAuditId is required");
        Objects.requireNonNull(scoreId, "scoreId is required");
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(userId, "userId is required");
        if (previousScoreVersion < 1 || newScoreVersion != previousScoreVersion + 1) {
            throw new IllegalArgumentException("newScoreVersion must equal previousScoreVersion + 1");
        }
        previousNetScore = Objects.requireNonNull(previousNetScore, "previousNetScore is required")
                .setScale(2, RoundingMode.HALF_UP);
        newNetScore = Objects.requireNonNull(newNetScore, "newNetScore is required")
                .setScale(2, RoundingMode.HALF_UP);
        if (previousCorrectCount < 0 || newCorrectCount < 0 || previousWrongCount < 0
                || newWrongCount < 0 || previousBlankCount < 0 || newBlankCount < 0) {
            throw new IllegalArgumentException("Audit counts must be non-negative");
        }
        Objects.requireNonNull(reasonCode, "reasonCode is required");
        if (reasonDetail == null || reasonDetail.isBlank()) {
            throw new IllegalArgumentException("reasonDetail must not be blank");
        }
        if (reasonDetail.trim().length() > 500) {
            throw new IllegalArgumentException("reasonDetail must not exceed 500 characters");
        }
        if (triggeredBy == null || triggeredBy.isBlank()) {
            throw new IllegalArgumentException("triggeredBy must not be blank");
        }
        if (triggeredBy.trim().length() > 120) {
            throw new IllegalArgumentException("triggeredBy must not exceed 120 characters");
        }
        deltaSummary = deltaSummary == null ? Map.of() : Map.copyOf(deltaSummary);
        if (trainingEligible) {
            throw new IllegalArgumentException("trainingEligible must be false per ADR-006");
        }
        Objects.requireNonNull(regradedAt, "regradedAt is required");
        reasonDetail = reasonDetail.trim();
        triggeredBy = triggeredBy.trim();
    }
}
