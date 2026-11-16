package com.verifiedai.exam.api;

import com.verifiedai.exam.domain.ActiveTrapImpactItem;
import com.verifiedai.exam.domain.ExamReadinessConfidenceTier;
import com.verifiedai.exam.domain.ExamReadinessProjection;
import com.verifiedai.exam.domain.ExamReadinessTopicAllocation;
import com.verifiedai.exam.domain.SectionReadinessBreakdown;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST response DTO for Sprint 9.6 Exam Readiness Projection (CNT-LOS-11).
 */
public record ExamReadinessResponse(
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
    public static ExamReadinessResponse from(ExamReadinessProjection projection) {
        return new ExamReadinessResponse(
                projection.projectionId(),
                projection.userId(),
                projection.examCode(),
                projection.targetId(),
                projection.latestScoredSessionId(),
                projection.totalQuestionCount(),
                projection.wrongAnswerPenaltyRatio(),
                projection.estimatedNetLow(),
                projection.estimatedNetMid(),
                projection.estimatedNetHigh(),
                projection.credibleIntervalLevel(),
                projection.targetNetScore(),
                projection.netGapToTarget(),
                projection.netLostToTraps(),
                projection.pacingFactor(),
                projection.meanRetentionProbability(),
                projection.epistemicConfidence(),
                projection.confidenceTier(),
                projection.calibrationRequired(),
                projection.recommendedDiagnosticQuestionCount(),
                projection.evaluatedSkillCount(),
                projection.totalEvidenceCount(),
                projection.completedMockCount(),
                projection.activeTrapBreakdown(),
                projection.sectionReadiness(),
                projection.topRoiAllocations(),
                projection.methodologyVersion(),
                false,
                0,
                projection.inferenceCostUsd(),
                projection.computedAt()
        );
    }
}
