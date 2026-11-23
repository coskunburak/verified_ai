package com.verifiedai.analytics.application;

import com.verifiedai.analytics.application.ProductMetricsSummaryView.ActivationFunnelView;
import com.verifiedai.analytics.application.ProductMetricsSummaryView.ConversionAndUnitEconomicsView;
import com.verifiedai.analytics.application.ProductMetricsSummaryView.LearningOsKpisView;
import com.verifiedai.analytics.application.ProductMetricsSummaryView.NorthStarView;
import com.verifiedai.analytics.application.ProductMetricsSummaryView.QualityAndLatencyView;
import com.verifiedai.analytics.application.ProductMetricsSummaryView.RateMetricView;
import com.verifiedai.analytics.application.ProductMetricsSummaryView.RetentionView;
import com.verifiedai.analytics.application.ProductMetricsSummaryView.TierUnitEconomicsView;
import com.verifiedai.analytics.application.ProductMetricsSummaryView.TransferGainMetricView;
import com.verifiedai.analytics.domain.ProductMetricsCalculator;
import com.verifiedai.analytics.domain.ProductMetricsCalculator.ComputedMetrics;
import com.verifiedai.analytics.domain.ProductMetricsCalculator.RateMetric;
import com.verifiedai.analytics.domain.ProductMetricsCalculator.RawMetricsInput;
import com.verifiedai.analytics.domain.ProductMetricsCalculator.TierUnitEconomics;
import com.verifiedai.analytics.infrastructure.AnalyticsProperties;
import com.verifiedai.analytics.infrastructure.ProductMetricsJdbcRepository;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only application query service for admin product metrics, Learning OS KPIs, and AI unit economics.
 */
@Service
public class ProductMetricsQueryService {

    private static final Set<String> ALLOWED_TIERS = Set.of(
        "ALL", "FREE", "PRO", "COACH_PRO", "YKS_SEASON_PASS", "UNKNOWN"
    );

    private final ProductMetricsJdbcRepository repository;
    private final AnalyticsProperties properties;
    private final AnalyticsMetrics metrics;
    private final Clock clock;

    public ProductMetricsQueryService(
        ProductMetricsJdbcRepository repository,
        AnalyticsProperties properties,
        AnalyticsMetrics metrics,
        Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional(readOnly = true)
    public ProductMetricsSummaryView getProductMetrics(LocalDate from, LocalDate to, String tier) {
        if (!properties.productMetricsEnabled()) {
            throw new ApiProblemException(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.ANALYTICS_FEATURE_DISABLED,
                "Analytics product metrics query is disabled",
                true,
                "RETRY_LATER"
            );
        }

        Instant start = clock.instant();
        LocalDate todayUtc = LocalDate.ofInstant(start, ZoneOffset.UTC);
        LocalDate effectiveTo = to != null ? to : todayUtc;
        LocalDate effectiveFrom = from != null ? from : effectiveTo.minusDays(6);

        if (effectiveFrom.isAfter(effectiveTo)) {
            throw invalidWindow("Query parameter 'from' must not be after 'to'");
        }
        long windowDays = ChronoUnit.DAYS.between(effectiveFrom, effectiveTo) + 1L;
        if (windowDays > properties.maxMetricsWindowDays()) {
            throw invalidWindow(
                "Requested metrics window (" + windowDays + " days) exceeds maximum of "
                    + properties.maxMetricsWindowDays() + " days"
            );
        }

        String normalizedTier = (tier == null || tier.isBlank())
            ? "ALL"
            : tier.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_TIERS.contains(normalizedTier)) {
            throw invalidWindow("Unsupported analytics tier filter: " + normalizedTier);
        }

        RawMetricsInput raw = repository.loadMetricsInput(effectiveFrom, effectiveTo, normalizedTier);
        Instant generatedAt = clock.instant();
        ComputedMetrics computed = ProductMetricsCalculator.compute(
            effectiveFrom,
            effectiveTo,
            normalizedTier,
            raw,
            generatedAt
        );

        Duration elapsed = Duration.between(start, generatedAt);
        metrics.recordMetricsQueryDuration(normalizedTier, elapsed.isNegative() ? Duration.ZERO : elapsed);
        return toView(computed);
    }

    private static ProductMetricsSummaryView toView(ComputedMetrics computed) {
        return new ProductMetricsSummaryView(
            computed.schemaVersion(),
            computed.fromDate(),
            computed.toDate(),
            computed.tierFilter(),
            new ActivationFunnelView(
                computed.activationFunnel().onboardingStartedUsers(),
                computed.activationFunnel().onboardingCompletedUsers(),
                computed.activationFunnel().firstProblemCapturedUsers(),
                computed.activationFunnel().firstSolutionCompletedUsers(),
                computed.activationFunnel().verificationDetailsViewedUsers(),
                computed.activationFunnel().secondSessionUsers(),
                computed.activationFunnel().subscribedUsers(),
                computed.activationFunnel().onboardingCompletionRate(),
                computed.activationFunnel().activationToFirstSolveRate(),
                computed.activationFunnel().trialOrPaidConversionRate()
            ),
            new NorthStarView(
                computed.northStar().weeklyVerifiedLearningSessions(),
                computed.northStar().activeLearners(),
                computed.northStar().skillsImprovedCount(),
                computed.northStar().skillsImprovedPerActiveLearner()
            ),
            new LearningOsKpisView(
                toRateView(computed.learningOsKpis().vitrr()),
                toRateView(computed.learningOsKpis().drpr48h()),
                toRateView(computed.learningOsKpis().drpr7d()),
                toRateView(computed.learningOsKpis().mrrr14d()),
                new TransferGainMetricView(
                    computed.learningOsKpis().transferGainIndex().variantAPassed(),
                    computed.learningOsKpis().transferGainIndex().variantATotal(),
                    computed.learningOsKpis().transferGainIndex().variantAAccuracy(),
                    computed.learningOsKpis().transferGainIndex().variantBCPassed(),
                    computed.learningOsKpis().transferGainIndex().variantBCTotal(),
                    computed.learningOsKpis().transferGainIndex().variantBCAccuracy(),
                    computed.learningOsKpis().transferGainIndex().transferGainIndex(),
                    computed.learningOsKpis().transferGainIndex().insufficientData()
                )
            ),
            new RetentionView(
                toRateView(computed.retention().d1Retention()),
                toRateView(computed.retention().d7Retention()),
                toRateView(computed.retention().d30Retention()),
                computed.retention().repeatMistakeReviewLearners(),
                computed.retention().studyPlanItemsCompleted()
            ),
            new QualityAndLatencyView(
                toRateView(computed.qualityAndLatency().parseCorrectionRate()),
                toRateView(computed.qualityAndLatency().solveFailureRate()),
                computed.qualityAndLatency().solveLatencyP50Ms(),
                computed.qualityAndLatency().solveLatencyP95Ms(),
                computed.qualityAndLatency().latencySampleCount(),
                computed.qualityAndLatency().insufficientLatencyData()
            ),
            new ConversionAndUnitEconomicsView(
                toRateView(computed.conversionAndUnitEconomics().paywallToPurchaseRate()),
                computed.conversionAndUnitEconomics().totalCompletedSolves(),
                computed.conversionAndUnitEconomics().totalVerifiedLearningSessions(),
                computed.conversionAndUnitEconomics().payingActiveLearners(),
                computed.conversionAndUnitEconomics().totalAiCostUsd(),
                computed.conversionAndUnitEconomics().aiCostPerCompletedSolveUsd(),
                computed.conversionAndUnitEconomics().aiCostPerPayingActiveLearnerUsd(),
                computed.conversionAndUnitEconomics().aiCostPerVerifiedLearningSessionUsd(),
                computed.conversionAndUnitEconomics().byTier().stream().map(ProductMetricsQueryService::toTierView).toList()
            ),
            computed.llmTokensUsed(),
            computed.trainingEligible(),
            computed.generatedAt()
        );
    }

    private static RateMetricView toRateView(RateMetric rate) {
        return new RateMetricView(rate.numerator(), rate.denominator(), rate.rate(), rate.insufficientData());
    }

    private static TierUnitEconomicsView toTierView(TierUnitEconomics tier) {
        return new TierUnitEconomicsView(
            tier.tier(),
            tier.activeLearners(),
            tier.completedSolves(),
            tier.verifiedLearningSessions(),
            tier.totalAiCostUsd(),
            tier.aiCostPerCompletedSolveUsd(),
            tier.aiCostPerActiveLearnerUsd(),
            tier.aiCostPerVerifiedSessionUsd(),
            tier.grossMarginPercentProxy(),
            tier.insufficientData()
        );
    }

    private static ApiProblemException invalidWindow(String message) {
        return new ApiProblemException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.ANALYTICS_METRICS_WINDOW_INVALID,
            message,
            false,
            "ADJUST_METRICS_WINDOW"
        );
    }
}
