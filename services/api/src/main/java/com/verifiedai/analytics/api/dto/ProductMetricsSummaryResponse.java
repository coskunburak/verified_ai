package com.verifiedai.analytics.api.dto;

import com.verifiedai.analytics.application.ProductMetricsSummaryView;
import com.verifiedai.analytics.application.ProductMetricsSummaryView.RateMetricView;
import com.verifiedai.analytics.application.ProductMetricsSummaryView.TierUnitEconomicsView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ProductMetricsSummaryResponse(
    String schemaVersion,
    LocalDate fromDate,
    LocalDate toDate,
    String tierFilter,
    ActivationFunnelDto activationFunnel,
    NorthStarDto northStar,
    LearningOsKpisDto learningOsKpis,
    RetentionDto retention,
    QualityAndLatencyDto qualityAndLatency,
    ConversionAndUnitEconomicsDto conversionAndUnitEconomics,
    int llmTokensUsed,
    boolean trainingEligible,
    Instant generatedAt
) {
    public static ProductMetricsSummaryResponse from(ProductMetricsSummaryView view) {
        return new ProductMetricsSummaryResponse(
            view.schemaVersion(),
            view.fromDate(),
            view.toDate(),
            view.tierFilter(),
            new ActivationFunnelDto(
                view.activationFunnel().onboardingStartedUsers(),
                view.activationFunnel().onboardingCompletedUsers(),
                view.activationFunnel().firstProblemCapturedUsers(),
                view.activationFunnel().firstSolutionCompletedUsers(),
                view.activationFunnel().verificationDetailsViewedUsers(),
                view.activationFunnel().secondSessionUsers(),
                view.activationFunnel().subscribedUsers(),
                view.activationFunnel().onboardingCompletionRate(),
                view.activationFunnel().activationToFirstSolveRate(),
                view.activationFunnel().trialOrPaidConversionRate()
            ),
            new NorthStarDto(
                view.northStar().weeklyVerifiedLearningSessions(),
                view.northStar().activeLearners(),
                view.northStar().skillsImprovedCount(),
                view.northStar().skillsImprovedPerActiveLearner()
            ),
            new LearningOsKpisDto(
                RateMetricDto.from(view.learningOsKpis().vitrr()),
                RateMetricDto.from(view.learningOsKpis().drpr48h()),
                RateMetricDto.from(view.learningOsKpis().drpr7d()),
                RateMetricDto.from(view.learningOsKpis().mrrr14d()),
                new TransferGainMetricDto(
                    view.learningOsKpis().transferGainIndex().variantAPassed(),
                    view.learningOsKpis().transferGainIndex().variantATotal(),
                    view.learningOsKpis().transferGainIndex().variantAAccuracy(),
                    view.learningOsKpis().transferGainIndex().variantBCPassed(),
                    view.learningOsKpis().transferGainIndex().variantBCTotal(),
                    view.learningOsKpis().transferGainIndex().variantBCAccuracy(),
                    view.learningOsKpis().transferGainIndex().transferGainIndex(),
                    view.learningOsKpis().transferGainIndex().insufficientData()
                )
            ),
            new RetentionDto(
                RateMetricDto.from(view.retention().d1Retention()),
                RateMetricDto.from(view.retention().d7Retention()),
                RateMetricDto.from(view.retention().d30Retention()),
                view.retention().repeatMistakeReviewLearners(),
                view.retention().studyPlanItemsCompleted()
            ),
            new QualityAndLatencyDto(
                RateMetricDto.from(view.qualityAndLatency().parseCorrectionRate()),
                RateMetricDto.from(view.qualityAndLatency().solveFailureRate()),
                view.qualityAndLatency().solveLatencyP50Ms(),
                view.qualityAndLatency().solveLatencyP95Ms(),
                view.qualityAndLatency().latencySampleCount(),
                view.qualityAndLatency().insufficientLatencyData()
            ),
            new ConversionAndUnitEconomicsDto(
                RateMetricDto.from(view.conversionAndUnitEconomics().paywallToPurchaseRate()),
                view.conversionAndUnitEconomics().totalCompletedSolves(),
                view.conversionAndUnitEconomics().totalVerifiedLearningSessions(),
                view.conversionAndUnitEconomics().payingActiveLearners(),
                view.conversionAndUnitEconomics().totalAiCostUsd(),
                view.conversionAndUnitEconomics().aiCostPerCompletedSolveUsd(),
                view.conversionAndUnitEconomics().aiCostPerPayingActiveLearnerUsd(),
                view.conversionAndUnitEconomics().aiCostPerVerifiedLearningSessionUsd(),
                view.conversionAndUnitEconomics().byTier().stream().map(TierUnitEconomicsDto::from).toList()
            ),
            view.llmTokensUsed(),
            view.trainingEligible(),
            view.generatedAt()
        );
    }

    public record RateMetricDto(
        long numerator,
        long denominator,
        Double rate,
        boolean insufficientData
    ) {
        public static RateMetricDto from(RateMetricView view) {
            return new RateMetricDto(view.numerator(), view.denominator(), view.rate(), view.insufficientData());
        }
    }

    public record TransferGainMetricDto(
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

    public record ActivationFunnelDto(
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

    public record NorthStarDto(
        long weeklyVerifiedLearningSessions,
        long activeLearners,
        long skillsImprovedCount,
        Double skillsImprovedPerActiveLearner
    ) {
    }

    public record LearningOsKpisDto(
        RateMetricDto vitrr,
        RateMetricDto drpr48h,
        RateMetricDto drpr7d,
        RateMetricDto mrrr14d,
        TransferGainMetricDto transferGainIndex
    ) {
    }

    public record RetentionDto(
        RateMetricDto d1Retention,
        RateMetricDto d7Retention,
        RateMetricDto d30Retention,
        long repeatMistakeReviewLearners,
        long studyPlanItemsCompleted
    ) {
    }

    public record QualityAndLatencyDto(
        RateMetricDto parseCorrectionRate,
        RateMetricDto solveFailureRate,
        Integer solveLatencyP50Ms,
        Integer solveLatencyP95Ms,
        long latencySampleCount,
        boolean insufficientLatencyData
    ) {
    }

    public record TierUnitEconomicsDto(
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
        public static TierUnitEconomicsDto from(TierUnitEconomicsView view) {
            return new TierUnitEconomicsDto(
                view.tier(),
                view.activeLearners(),
                view.completedSolves(),
                view.verifiedLearningSessions(),
                view.totalAiCostUsd(),
                view.aiCostPerCompletedSolveUsd(),
                view.aiCostPerActiveLearnerUsd(),
                view.aiCostPerVerifiedSessionUsd(),
                view.grossMarginPercentProxy(),
                view.insufficientData()
            );
        }
    }

    public record ConversionAndUnitEconomicsDto(
        RateMetricDto paywallToPurchaseRate,
        long totalCompletedSolves,
        long totalVerifiedLearningSessions,
        long payingActiveLearners,
        double totalAiCostUsd,
        Double aiCostPerCompletedSolveUsd,
        Double aiCostPerPayingActiveLearnerUsd,
        Double aiCostPerVerifiedLearningSessionUsd,
        List<TierUnitEconomicsDto> byTier
    ) {
    }
}
