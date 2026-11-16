package com.verifiedai.exam.events;

import com.verifiedai.exam.domain.ExamReadinessConfidenceTier;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain event published when an exam readiness projection is computed ($0.00 LLM cost, trainingEligible=false).
 */
public record ExamReadinessComputedEvent(
        UUID projectionId,
        UUID userId,
        String examCode,
        BigDecimal estimatedNetLow,
        BigDecimal estimatedNetMid,
        BigDecimal estimatedNetHigh,
        BigDecimal netLostToTraps,
        BigDecimal epistemicConfidence,
        ExamReadinessConfidenceTier confidenceTier,
        boolean calibrationRequired,
        boolean trainingEligible,
        Instant computedAt
) {
    public ExamReadinessComputedEvent {
        Objects.requireNonNull(projectionId, "projectionId is required");
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(examCode, "examCode is required");
        Objects.requireNonNull(estimatedNetLow, "estimatedNetLow is required");
        Objects.requireNonNull(estimatedNetMid, "estimatedNetMid is required");
        Objects.requireNonNull(estimatedNetHigh, "estimatedNetHigh is required");
        Objects.requireNonNull(netLostToTraps, "netLostToTraps is required");
        Objects.requireNonNull(epistemicConfidence, "epistemicConfidence is required");
        Objects.requireNonNull(confidenceTier, "confidenceTier is required");
        if (trainingEligible) {
            throw new IllegalArgumentException("trainingEligible must be false per ADR-006");
        }
        Objects.requireNonNull(computedAt, "computedAt is required");
    }
}
