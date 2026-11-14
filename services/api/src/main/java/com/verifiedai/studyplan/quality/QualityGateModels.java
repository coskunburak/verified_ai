package com.verifiedai.studyplan.quality;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Schema-aligned domain records and DTOs for Sprint 8.10 Phase 08 Exit Gate
 * ({@code study-plan-quality-v1}). Zero PII, trainingEligible always false, llmTokensUsed always 0.
 */
public final class QualityGateModels {

    public static final String SCHEMA_VERSION = "study-plan-quality-v1";

    private QualityGateModels() {
    }

    public enum GateStatus {
        PASS,
        WARN,
        FAIL
    }

    public enum RegressionSeverity {
        P0,
        P1
    }

    public record ContractGateCheck(
            String contractId,
            String title,
            GateStatus status,
            RegressionSeverity severity,
            double observedValue,
            String thresholdDescription,
            String details
    ) {
    }

    public record QualityTelemetryMetrics(
            int coldStartCompletionCount,
            double coldStartZeroCostComplianceRate,
            double recommendationExplainabilityRate,
            double spacedReviewOnTimeCompletionRate,
            double dailyCoachCompletionRate,
            double rebalanceRecoveryRate,
            double microLessonCompletionRate,
            double studySessionRecoverySuccessRate,
            double independentStreakRetentionRate,
            double weeklyDigestZeroPiiComplianceRate,
            int piiViolationCount
    ) {
    }

    public record StudyPlanQualityGateReport(
            String schemaVersion,
            UUID snapshotId,
            Instant evaluatedAt,
            GateStatus overallStatus,
            int p0RegressionCount,
            int p1RegressionCount,
            int llmTokensUsed,
            boolean trainingEligible,
            List<ContractGateCheck> contractChecks,
            QualityTelemetryMetrics telemetryMetrics
    ) {
    }

    public record QualityGateRawAggregateSnapshot(
            int coldStartTotalCount,
            int coldStartViolationCount,
            int recommendationTotalCount,
            int recommendationMissingRationaleCount,
            int spacedReviewTotalCount,
            int spacedReviewCompletedCount,
            int spacedReviewOverdueCount,
            int dailyCoachTotalCount,
            int dailyCoachCompletedCount,
            int studySessionTotalCount,
            int studySessionViolationCount,
            int rebalanceTotalCount,
            int rebalanceInvalidBudgetCount,
            int microLessonCatalogTotalCount,
            int microLessonCatalogVerifiedCount,
            int microLessonCompletionCount,
            int streakTotalCount,
            int streakIndependentPositiveCount,
            int streakTrainingViolationCount,
            int weeklyDigestTotalCount,
            int weeklyDigestTrainingViolationCount,
            int weeklyDigestPiiViolationCount
    ) {
    }
}
