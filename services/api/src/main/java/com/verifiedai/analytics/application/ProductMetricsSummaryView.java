package com.verifiedai.analytics.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Immutable application read view matching product-metrics-summary-v1.schema.json.
 */
public record ProductMetricsSummaryView(
    String schemaVersion,
    LocalDate fromDate,
    LocalDate toDate,
    String tierFilter,
    ActivationFunnelView activationFunnel,
    NorthStarView northStar,
    LearningOsKpisView learningOsKpis,
    RetentionView retention,
    QualityAndLatencyView qualityAndLatency,
    ConversionAndUnitEconomicsView conversionAndUnitEconomics,
    int llmTokensUsed,
    boolean trainingEligible,
    Instant generatedAt
) {
    public record RateMetricView(
        long numerator,
        long denominator,
        Double rate,
        boolean insufficientData
    ) {
    }

    public record TransferGainMetricView(
        long variantAPassed,
        long variantATotal,
        Double variantAAccuracy,
        long variantBCPassed,
        long variantBCTotal,
        Double variantBCAccuracy,
        Double transferGainIndex,
        boolean insufficientData
    ) {
    }

    public record ActivationFunnelView(
        long onboardingStartedUsers,
        long onboardingCompletedUsers,
        long firstProblemCapturedUsers,
        long firstSolutionCompletedUsers,
        long verificationDetailsViewedUsers,
        long secondSessionUsers,
        long subscribedUsers,
        Double onboardingCompletionRate,
        Double activationToFirstSolveRate,
        Double trialOrPaidConversionRate
    ) {
    }

    public record NorthStarView(
        long weeklyVerifiedLearningSessions,
        long activeLearners,
        long skillsImprovedCount,
        Double skillsImprovedPerActiveLearner
    ) {
    }

    public record LearningOsKpisView(
        RateMetricView vitrr,
        RateMetricView drpr48h,
        RateMetricView drpr7d,
        RateMetricView mrrr14d,
        TransferGainMetricView transferGainIndex
    ) {
    }

    public record RetentionView(
        RateMetricView d1Retention,
        RateMetricView d7Retention,
        RateMetricView d30Retention,
        long repeatMistakeReviewLearners,
        long studyPlanItemsCompleted
    ) {
    }

    public record QualityAndLatencyView(
        RateMetricView parseCorrectionRate,
        RateMetricView solveFailureRate,
        Integer solveLatencyP50Ms,
        Integer solveLatencyP95Ms,
        long latencySampleCount,
        boolean insufficientLatencyData
    ) {
    }

    public record TierUnitEconomicsView(
        String tier,
        long activeLearners,
        long completedSolves,
        long verifiedLearningSessions,
        double totalAiCostUsd,
        Double aiCostPerCompletedSolveUsd,
        Double aiCostPerActiveLearnerUsd,
        Double aiCostPerVerifiedSessionUsd,
        Double grossMarginPercentProxy,
        boolean insufficientData
    ) {
    }

    public record ConversionAndUnitEconomicsView(
        RateMetricView paywallToPurchaseRate,
        long totalCompletedSolves,
        long totalVerifiedLearningSessions,
        long payingActiveLearners,
        double totalAiCostUsd,
        Double aiCostPerCompletedSolveUsd,
        Double aiCostPerPayingActiveLearnerUsd,
        Double aiCostPerVerifiedLearningSessionUsd,
        List<TierUnitEconomicsView> byTier
    ) {
        public ConversionAndUnitEconomicsView {
            byTier = byTier == null ? List.of() : List.copyOf(byTier);
        }
    }
}
