package com.verifiedai.analytics.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Pure deterministic product-KPI and unit-economics calculator enforcing INV-ANL-005 (honest zero-denominator
 * handling) and ADR-005/ADR-006 ($0.00 incremental LLM cost, training_eligible = false).
 */
public final class ProductMetricsCalculator {

    private static final BigDecimal MICROS_PER_USD = BigDecimal.valueOf(1_000_000L);
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100L);

    private ProductMetricsCalculator() {
    }

    public static ComputedMetrics compute(
        LocalDate fromDate,
        LocalDate toDate,
        String tierFilter,
        RawMetricsInput raw,
        Instant generatedAt
    ) {
        Objects.requireNonNull(fromDate, "fromDate must not be null");
        Objects.requireNonNull(toDate, "toDate must not be null");
        Objects.requireNonNull(tierFilter, "tierFilter must not be null");
        Objects.requireNonNull(raw, "raw must not be null");
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");

        ActivationFunnel funnel = new ActivationFunnel(
            raw.onboardingStartedUsers(),
            raw.onboardingCompletedUsers(),
            raw.firstProblemCapturedUsers(),
            raw.firstSolutionCompletedUsers(),
            raw.verificationDetailsViewedUsers(),
            raw.secondSessionUsers(),
            raw.subscribedUsers(),
            ratio(raw.onboardingCompletedUsers(), raw.onboardingStartedUsers()),
            ratio(raw.firstSolutionCompletedUsers(), raw.onboardingCompletedUsers()),
            ratio(raw.subscribedUsers(), raw.onboardingStartedUsers())
        );

        NorthStar northStar = new NorthStar(
            raw.weeklyVerifiedLearningSessions(),
            raw.activeLearners(),
            raw.skillsImprovedCount(),
            ratio(raw.skillsImprovedCount(), raw.activeLearners())
        );

        RateMetric vitrr = rateMetric(raw.vitrrIndependentPasses24h(), raw.vitrrMistakeDetectedTotal());
        RateMetric drpr48h = rateMetric(raw.drpr48hIndependentPasses(), raw.drpr48hScheduledOrCompletedTotal());
        RateMetric drpr7d = rateMetric(raw.drpr7dIndependentPasses(), raw.drpr7dScheduledOrCompletedTotal());

        Double mrrr14dValue = null;
        boolean mrrrInsufficient = true;
        if (raw.mrrrBaselineMistakeCount() > 0L) {
            mrrr14dValue = roundRate(
                BigDecimal.valueOf(raw.mrrrBaselineMistakeCount() - raw.mrrrFollowupMistakeCount())
                    .divide(BigDecimal.valueOf(raw.mrrrBaselineMistakeCount()), 4, RoundingMode.HALF_UP)
            );
            mrrrInsufficient = false;
        }
        RateMetric mrrr14d = new RateMetric(
            raw.mrrrFollowupMistakeCount(),
            raw.mrrrBaselineMistakeCount(),
            mrrr14dValue,
            mrrrInsufficient
        );

        Double variantAAccuracy = ratio(raw.tgiVariantAPassed(), raw.tgiVariantATotal());
        Double variantBCAccuracy = ratio(raw.tgiVariantBCPassed(), raw.tgiVariantBCTotal());
        Double tgiGain = (variantAAccuracy != null && variantBCAccuracy != null)
            ? roundRate(BigDecimal.valueOf(variantBCAccuracy).subtract(BigDecimal.valueOf(variantAAccuracy)))
            : null;
        TransferGainMetric tgi = new TransferGainMetric(
            raw.tgiVariantAPassed(),
            raw.tgiVariantATotal(),
            variantAAccuracy,
            raw.tgiVariantBCPassed(),
            raw.tgiVariantBCTotal(),
            variantBCAccuracy,
            tgiGain,
            variantAAccuracy == null || variantBCAccuracy == null
        );

        LearningOsKpis learningOsKpis = new LearningOsKpis(vitrr, drpr48h, drpr7d, mrrr14d, tgi);

        Retention retention = new Retention(
            rateMetric(raw.d1RetainedUsers(), raw.d1CohortSize()),
            rateMetric(raw.d7RetainedUsers(), raw.d7CohortSize()),
            rateMetric(raw.d30RetainedUsers(), raw.d30CohortSize()),
            raw.repeatMistakeReviewLearners(),
            raw.studyPlanItemsCompleted()
        );

        QualityAndLatency qualityAndLatency = new QualityAndLatency(
            rateMetric(raw.parseCorrectedCount(), raw.parseCompletedCount()),
            rateMetric(raw.solveFailedCount(), raw.solveRequestedCount()),
            raw.solveLatencyP50Ms(),
            raw.solveLatencyP95Ms(),
            raw.latencySampleCount(),
            raw.latencySampleCount() == 0L
        );

        List<TierUnitEconomics> tierEconomics = raw.tierEconomics().stream()
            .map(ProductMetricsCalculator::computeTierEconomics)
            .toList();

        long totalCompletedSolves = raw.tierEconomics().stream().mapToLong(RawTierEconomics::completedSolves).sum();
        long totalVerifiedSessions = raw.tierEconomics().stream().mapToLong(RawTierEconomics::verifiedLearningSessions).sum();
        long totalPayingLearners = raw.tierEconomics().stream()
            .filter(t -> !"FREE".equals(t.tier()) && !"UNKNOWN".equals(t.tier()))
            .mapToLong(RawTierEconomics::activeLearners)
            .sum();
        long totalAiCostMicros = raw.tierEconomics().stream().mapToLong(RawTierEconomics::aiCostMicros).sum();

        ConversionAndUnitEconomics conversion = new ConversionAndUnitEconomics(
            rateMetric(raw.purchaseCompletedUsers(), raw.paywallViewedUsers()),
            totalCompletedSolves,
            totalVerifiedSessions,
            totalPayingLearners,
            microsToUsd(totalAiCostMicros),
            costPerUnitUsd(totalAiCostMicros, totalCompletedSolves),
            costPerUnitUsd(totalAiCostMicros, totalPayingLearners),
            costPerUnitUsd(totalAiCostMicros, totalVerifiedSessions),
            tierEconomics
        );

        return new ComputedMetrics(
            "product-metrics-summary-v1",
            fromDate,
            toDate,
            tierFilter,
            funnel,
            northStar,
            learningOsKpis,
            retention,
            qualityAndLatency,
            conversion,
            0,
            false,
            generatedAt
        );
    }

    private static TierUnitEconomics computeTierEconomics(RawTierEconomics tier) {
        double totalCostUsd = microsToUsd(tier.aiCostMicros());
        Double costPerSolve = costPerUnitUsd(tier.aiCostMicros(), tier.completedSolves());
        Double costPerLearner = costPerUnitUsd(tier.aiCostMicros(), tier.activeLearners());
        Double costPerSession = costPerUnitUsd(tier.aiCostMicros(), tier.verifiedLearningSessions());
        boolean insufficient = tier.completedSolves() == 0L && tier.activeLearners() == 0L;

        Double grossMarginProxy = null;
        if (tier.estimatedMonthlyPriceUsd() != null && tier.estimatedMonthlyPriceUsd() > 0.0 && tier.activeLearners() > 0L) {
            BigDecimal estimatedRevenue = BigDecimal.valueOf(tier.estimatedMonthlyPriceUsd())
                .multiply(BigDecimal.valueOf(tier.activeLearners()));
            BigDecimal cost = BigDecimal.valueOf(totalCostUsd);
            grossMarginProxy = estimatedRevenue.subtract(cost)
                .divide(estimatedRevenue, 4, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
        }

        return new TierUnitEconomics(
            tier.tier(),
            tier.activeLearners(),
            tier.completedSolves(),
            tier.verifiedLearningSessions(),
            totalCostUsd,
            costPerSolve,
            costPerLearner,
            costPerSession,
            grossMarginProxy,
            insufficient
        );
    }

    private static RateMetric rateMetric(long numerator, long denominator) {
        Double value = ratio(numerator, denominator);
        return new RateMetric(numerator, denominator, value, value == null);
    }

    private static Double ratio(long numerator, long denominator) {
        if (denominator <= 0L) {
            return null;
        }
        return roundRate(
            BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP)
        );
    }

    private static Double roundRate(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    private static double microsToUsd(long micros) {
        return BigDecimal.valueOf(micros)
            .divide(MICROS_PER_USD, 6, RoundingMode.HALF_UP)
            .doubleValue();
    }

    private static Double costPerUnitUsd(long micros, long denominator) {
        if (denominator <= 0L) {
            return null;
        }
        return BigDecimal.valueOf(micros)
            .divide(MICROS_PER_USD, 8, RoundingMode.HALF_UP)
            .divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP)
            .doubleValue();
    }

    public record RawMetricsInput(
        long onboardingStartedUsers,
        long onboardingCompletedUsers,
        long firstProblemCapturedUsers,
        long firstSolutionCompletedUsers,
        long verificationDetailsViewedUsers,
        long secondSessionUsers,
        long subscribedUsers,
        long weeklyVerifiedLearningSessions,
        long activeLearners,
        long skillsImprovedCount,
        long vitrrIndependentPasses24h,
        long vitrrMistakeDetectedTotal,
        long drpr48hIndependentPasses,
        long drpr48hScheduledOrCompletedTotal,
        long drpr7dIndependentPasses,
        long drpr7dScheduledOrCompletedTotal,
        long mrrrBaselineMistakeCount,
        long mrrrFollowupMistakeCount,
        long tgiVariantAPassed,
        long tgiVariantATotal,
        long tgiVariantBCPassed,
        long tgiVariantBCTotal,
        long d1RetainedUsers,
        long d1CohortSize,
        long d7RetainedUsers,
        long d7CohortSize,
        long d30RetainedUsers,
        long d30CohortSize,
        long repeatMistakeReviewLearners,
        long studyPlanItemsCompleted,
        long parseCorrectedCount,
        long parseCompletedCount,
        long solveFailedCount,
        long solveRequestedCount,
        Integer solveLatencyP50Ms,
        Integer solveLatencyP95Ms,
        long latencySampleCount,
        long purchaseCompletedUsers,
        long paywallViewedUsers,
        List<RawTierEconomics> tierEconomics
    ) {
        public RawMetricsInput {
            tierEconomics = tierEconomics == null ? List.of() : List.copyOf(tierEconomics);
        }
    }

    public record RawTierEconomics(
        String tier,
        long activeLearners,
        long completedSolves,
        long verifiedLearningSessions,
        long aiCostMicros,
        Double estimatedMonthlyPriceUsd
    ) {
    }

    public record RateMetric(
        long numerator,
        long denominator,
        Double rate,
        boolean insufficientData
    ) {
    }

    public record TransferGainMetric(
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

    public record ActivationFunnel(
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

    public record NorthStar(
        long weeklyVerifiedLearningSessions,
        long activeLearners,
        long skillsImprovedCount,
        Double skillsImprovedPerActiveLearner
    ) {
    }

    public record LearningOsKpis(
        RateMetric vitrr,
        RateMetric drpr48h,
        RateMetric drpr7d,
        RateMetric mrrr14d,
        TransferGainMetric transferGainIndex
    ) {
    }

    public record Retention(
        RateMetric d1Retention,
        RateMetric d7Retention,
        RateMetric d30Retention,
        long repeatMistakeReviewLearners,
        long studyPlanItemsCompleted
    ) {
    }

    public record QualityAndLatency(
        RateMetric parseCorrectionRate,
        RateMetric solveFailureRate,
        Integer solveLatencyP50Ms,
        Integer solveLatencyP95Ms,
        long latencySampleCount,
        boolean insufficientLatencyData
    ) {
    }

    public record TierUnitEconomics(
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

    public record ConversionAndUnitEconomics(
        RateMetric paywallToPurchaseRate,
        long totalCompletedSolves,
        long totalVerifiedLearningSessions,
        long payingActiveLearners,
        double totalAiCostUsd,
        Double aiCostPerCompletedSolveUsd,
        Double aiCostPerPayingActiveLearnerUsd,
        Double aiCostPerVerifiedLearningSessionUsd,
        List<TierUnitEconomics> byTier
    ) {
    }

    public record ComputedMetrics(
        String schemaVersion,
        LocalDate fromDate,
        LocalDate toDate,
        String tierFilter,
        ActivationFunnel activationFunnel,
        NorthStar northStar,
        LearningOsKpis learningOsKpis,
        Retention retention,
        QualityAndLatency qualityAndLatency,
        ConversionAndUnitEconomics conversionAndUnitEconomics,
        int llmTokensUsed,
        boolean trainingEligible,
        Instant generatedAt
    ) {
    }
}
