package com.verifiedai.exam.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable domain projection for Sprint 9.6 Exam Readiness & 90% Credible Interval Net Projection.
 * Enforces INV-EXAM-001 (epistemic honesty / PRELIMINARY guard), INV-EXAM-004 ($0.00 LLM cost),
 * OUT-REQ-001 (mandatory credible interval), and ADR-006 (trainingEligible=false).
 */
public record ExamReadinessProjection(
        UUID projectionId,
        UUID userId,
        String examCode,
        UUID targetId,
        UUID latestScoredSessionId,
        int totalQuestionCount,
        BigDecimal wrongAnswerPenaltyRatio,
        BigDecimal estimatedNetLow,
        BigDecimal estimatedNetMid,
        BigDecimal estimatedNetHigh,
        BigDecimal credibleIntervalLevel,
        BigDecimal targetNetScore,
        BigDecimal netGapToTarget,
        BigDecimal netLostToTraps,
        BigDecimal pacingFactor,
        BigDecimal meanRetentionProbability,
        BigDecimal epistemicConfidence,
        ExamReadinessConfidenceTier confidenceTier,
        boolean calibrationRequired,
        int recommendedDiagnosticQuestionCount,
        int evaluatedSkillCount,
        int totalEvidenceCount,
        int completedMockCount,
        List<ActiveTrapImpactItem> activeTrapBreakdown,
        List<SectionReadinessBreakdown> sectionReadiness,
        List<ExamReadinessTopicAllocation> topRoiAllocations,
        String methodologyVersion,
        boolean trainingEligible,
        int llmTokensUsed,
        BigDecimal inferenceCostUsd,
        Instant computedAt
) {
    private static final BigDecimal PRELIMINARY_THRESHOLD = new BigDecimal("0.2500");
    private static final BigDecimal EXPECTED_CREDIBLE_LEVEL = new BigDecimal("0.90");

    public ExamReadinessProjection {
        Objects.requireNonNull(projectionId, "projectionId is required");
        Objects.requireNonNull(userId, "userId is required");
        if (examCode == null || examCode.isBlank()) {
            throw new IllegalArgumentException("examCode must not be blank");
        }
        if (totalQuestionCount <= 0) {
            throw new IllegalArgumentException("totalQuestionCount must be > 0");
        }
        wrongAnswerPenaltyRatio = Objects.requireNonNull(wrongAnswerPenaltyRatio, "wrongAnswerPenaltyRatio is required")
                .setScale(4, RoundingMode.HALF_UP);
        if (wrongAnswerPenaltyRatio.compareTo(BigDecimal.ZERO) < 0
                || wrongAnswerPenaltyRatio.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("wrongAnswerPenaltyRatio must be in [0.0000, 1.0000]");
        }
        estimatedNetLow = Objects.requireNonNull(estimatedNetLow, "estimatedNetLow is required")
                .setScale(2, RoundingMode.HALF_UP);
        estimatedNetMid = Objects.requireNonNull(estimatedNetMid, "estimatedNetMid is required")
                .setScale(2, RoundingMode.HALF_UP);
        estimatedNetHigh = Objects.requireNonNull(estimatedNetHigh, "estimatedNetHigh is required")
                .setScale(2, RoundingMode.HALF_UP);
        if (estimatedNetLow.compareTo(estimatedNetMid) > 0
                || estimatedNetMid.compareTo(estimatedNetHigh) > 0) {
            throw new IllegalArgumentException("Credible interval must satisfy estimatedNetLow <= estimatedNetMid <= estimatedNetHigh");
        }

        BigDecimal minPossibleNet = BigDecimal.valueOf(totalQuestionCount)
                .multiply(wrongAnswerPenaltyRatio)
                .negate();
        BigDecimal maxPossibleNet = BigDecimal.valueOf(totalQuestionCount).setScale(2, RoundingMode.HALF_UP);
        if (estimatedNetLow.compareTo(minPossibleNet) < 0 || estimatedNetHigh.compareTo(maxPossibleNet) > 0) {
            throw new IllegalArgumentException("Credible interval bounds exceed exam net range");
        }

        credibleIntervalLevel = Objects.requireNonNull(credibleIntervalLevel, "credibleIntervalLevel is required")
                .setScale(2, RoundingMode.HALF_UP);
        if (credibleIntervalLevel.compareTo(EXPECTED_CREDIBLE_LEVEL) != 0) {
            throw new IllegalArgumentException("credibleIntervalLevel must equal 0.90");
        }

        if (targetNetScore != null) {
            targetNetScore = targetNetScore.setScale(2, RoundingMode.HALF_UP);
        }
        if (netGapToTarget != null) {
            netGapToTarget = netGapToTarget.setScale(2, RoundingMode.HALF_UP);
        }

        netLostToTraps = Objects.requireNonNull(netLostToTraps, "netLostToTraps is required")
                .setScale(2, RoundingMode.HALF_UP);
        if (netLostToTraps.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("netLostToTraps must be non-negative");
        }

        pacingFactor = Objects.requireNonNull(pacingFactor, "pacingFactor is required")
                .setScale(4, RoundingMode.HALF_UP);
        if (pacingFactor.compareTo(BigDecimal.ZERO) <= 0 || pacingFactor.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("pacingFactor must be in (0.0000, 1.0000]");
        }

        meanRetentionProbability = normalizeUnit(meanRetentionProbability, "meanRetentionProbability");
        epistemicConfidence = normalizeUnit(epistemicConfidence, "epistemicConfidence");
        Objects.requireNonNull(confidenceTier, "confidenceTier is required");

        if (recommendedDiagnosticQuestionCount < 0) {
            throw new IllegalArgumentException("recommendedDiagnosticQuestionCount must be >= 0");
        }
        if (epistemicConfidence.compareTo(PRELIMINARY_THRESHOLD) < 0) {
            if (confidenceTier != ExamReadinessConfidenceTier.PRELIMINARY
                    || !calibrationRequired
                    || recommendedDiagnosticQuestionCount < 10) {
                throw new IllegalArgumentException(
                        "INV-EXAM-001 violation: epistemicConfidence < 0.25 requires PRELIMINARY tier, calibrationRequired=true, and >=10 diagnostic questions"
                );
            }
        }

        if (evaluatedSkillCount < 0 || totalEvidenceCount < 0 || completedMockCount < 0) {
            throw new IllegalArgumentException("Counts must be non-negative");
        }

        activeTrapBreakdown = activeTrapBreakdown == null ? List.of() : List.copyOf(activeTrapBreakdown);
        sectionReadiness = sectionReadiness == null ? List.of() : List.copyOf(sectionReadiness);
        topRoiAllocations = topRoiAllocations == null ? List.of() : List.copyOf(topRoiAllocations);

        if (methodologyVersion == null || methodologyVersion.isBlank()) {
            throw new IllegalArgumentException("methodologyVersion must not be blank");
        }
        if (trainingEligible) {
            throw new IllegalArgumentException("trainingEligible must be false per ADR-006");
        }
        if (llmTokensUsed != 0) {
            throw new IllegalArgumentException("llmTokensUsed must be 0 per INV-EXAM-004");
        }
        inferenceCostUsd = Objects.requireNonNull(inferenceCostUsd, "inferenceCostUsd is required")
                .setScale(6, RoundingMode.HALF_UP);
        if (inferenceCostUsd.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException("inferenceCostUsd must be 0.000000 per INV-EXAM-004");
        }
        Objects.requireNonNull(computedAt, "computedAt is required");
        examCode = examCode.trim();
        methodologyVersion = methodologyVersion.trim();
    }

    public BigDecimal credibleIntervalWidth() {
        return estimatedNetHigh.subtract(estimatedNetLow).setScale(2, RoundingMode.HALF_UP);
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
