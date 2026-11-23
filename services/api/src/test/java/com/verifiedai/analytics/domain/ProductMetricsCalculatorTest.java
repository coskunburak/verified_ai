package com.verifiedai.analytics.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.verifiedai.analytics.domain.ProductMetricsCalculator.ComputedMetrics;
import com.verifiedai.analytics.domain.ProductMetricsCalculator.RawMetricsInput;
import com.verifiedai.analytics.domain.ProductMetricsCalculator.RawTierEconomics;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProductMetricsCalculatorTest {

    private final LocalDate fromDate = LocalDate.of(2026, 11, 16);
    private final LocalDate toDate = LocalDate.of(2026, 11, 23);
    private final Instant generatedAt = Instant.parse("2026-11-23T10:00:00Z");

    @Test
    void computesFunnelLearningOsKpisRetentionAndTierUnitEconomicsDeterministically() {
        RawMetricsInput raw = new RawMetricsInput(
            100L, 80L, 70L, 60L, 40L, 35L, 10L,
            120L, 60L, 45L,
            15L, 20L,
            14L, 20L,
            9L, 15L,
            25L, 10L,
            16L, 20L,
            17L, 20L,
            40L, 80L,
            20L, 50L,
            5L, 25L,
            18L, 90L,
            3L, 60L,
            2L, 80L,
            1150, 3400, 78L,
            10L, 50L,
            List.of(
                new RawTierEconomics("FREE", 40L, 30L, 50L, 600_000L, null),
                new RawTierEconomics("PRO", 20L, 30L, 70L, 1_200_000L, 9.99)
            )
        );

        ComputedMetrics metrics = ProductMetricsCalculator.compute(fromDate, toDate, "ALL", raw, generatedAt);

        assertThat(metrics.schemaVersion()).isEqualTo("product-metrics-summary-v1");
        assertThat(metrics.llmTokensUsed()).isZero();
        assertThat(metrics.trainingEligible()).isFalse();

        assertThat(metrics.activationFunnel().onboardingCompletionRate()).isEqualTo(0.8);
        assertThat(metrics.activationFunnel().activationToFirstSolveRate()).isEqualTo(0.75);
        assertThat(metrics.activationFunnel().trialOrPaidConversionRate()).isEqualTo(0.1);

        assertThat(metrics.northStar().skillsImprovedPerActiveLearner()).isEqualTo(0.75);

        assertThat(metrics.learningOsKpis().vitrr().rate()).isEqualTo(0.75);
        assertThat(metrics.learningOsKpis().vitrr().insufficientData()).isFalse();
        assertThat(metrics.learningOsKpis().drpr48h().rate()).isEqualTo(0.7);
        assertThat(metrics.learningOsKpis().drpr7d().rate()).isEqualTo(0.6);
        assertThat(metrics.learningOsKpis().mrrr14d().rate()).isEqualTo(0.6);
        assertThat(metrics.learningOsKpis().transferGainIndex().variantAAccuracy()).isEqualTo(0.8);
        assertThat(metrics.learningOsKpis().transferGainIndex().variantBCAccuracy()).isEqualTo(0.85);
        assertThat(metrics.learningOsKpis().transferGainIndex().transferGainIndex()).isEqualTo(0.05);
        assertThat(metrics.learningOsKpis().transferGainIndex().insufficientData()).isFalse();

        assertThat(metrics.retention().d1Retention().rate()).isEqualTo(0.5);
        assertThat(metrics.retention().d7Retention().rate()).isEqualTo(0.4);
        assertThat(metrics.retention().d30Retention().rate()).isEqualTo(0.2);

        assertThat(metrics.qualityAndLatency().parseCorrectionRate().rate()).isEqualTo(0.05);
        assertThat(metrics.qualityAndLatency().solveFailureRate().rate()).isEqualTo(0.025);
        assertThat(metrics.qualityAndLatency().solveLatencyP50Ms()).isEqualTo(1150);
        assertThat(metrics.qualityAndLatency().solveLatencyP95Ms()).isEqualTo(3400);
        assertThat(metrics.qualityAndLatency().insufficientLatencyData()).isFalse();

        assertThat(metrics.conversionAndUnitEconomics().paywallToPurchaseRate().rate()).isEqualTo(0.2);
        assertThat(metrics.conversionAndUnitEconomics().totalAiCostUsd()).isEqualTo(1.8);
        assertThat(metrics.conversionAndUnitEconomics().aiCostPerCompletedSolveUsd()).isEqualTo(0.03);
        assertThat(metrics.conversionAndUnitEconomics().aiCostPerPayingActiveLearnerUsd()).isEqualTo(0.09);
        assertThat(metrics.conversionAndUnitEconomics().aiCostPerVerifiedLearningSessionUsd()).isEqualTo(0.015);

        assertThat(metrics.conversionAndUnitEconomics().byTier()).hasSize(2);
        assertThat(metrics.conversionAndUnitEconomics().byTier().get(0).grossMarginPercentProxy()).isNull();
        assertThat(metrics.conversionAndUnitEconomics().byTier().get(1).aiCostPerCompletedSolveUsd()).isEqualTo(0.04);
        assertThat(metrics.conversionAndUnitEconomics().byTier().get(1).grossMarginPercentProxy()).isNotNull();
    }

    @Test
    void returnsNullRatesAndInsufficientDataTrueWhenDenominatorsAreZero() {
        RawMetricsInput empty = new RawMetricsInput(
            0L, 0L, 0L, 0L, 0L, 0L, 0L,
            0L, 0L, 0L,
            0L, 0L,
            0L, 0L,
            0L, 0L,
            0L, 0L,
            0L, 0L,
            0L, 0L,
            0L, 0L,
            0L, 0L,
            0L, 0L,
            0L, 0L,
            0L, 0L,
            0L, 0L,
            null, null, 0L,
            0L, 0L,
            List.of(new RawTierEconomics("FREE", 0L, 0L, 0L, 0L, null))
        );

        ComputedMetrics metrics = ProductMetricsCalculator.compute(fromDate, toDate, "ALL", empty, generatedAt);

        assertThat(metrics.activationFunnel().onboardingCompletionRate()).isNull();
        assertThat(metrics.activationFunnel().activationToFirstSolveRate()).isNull();
        assertThat(metrics.activationFunnel().trialOrPaidConversionRate()).isNull();
        assertThat(metrics.northStar().skillsImprovedPerActiveLearner()).isNull();
        assertThat(metrics.learningOsKpis().vitrr().rate()).isNull();
        assertThat(metrics.learningOsKpis().vitrr().insufficientData()).isTrue();
        assertThat(metrics.learningOsKpis().drpr48h().insufficientData()).isTrue();
        assertThat(metrics.learningOsKpis().drpr7d().insufficientData()).isTrue();
        assertThat(metrics.learningOsKpis().mrrr14d().insufficientData()).isTrue();
        assertThat(metrics.learningOsKpis().transferGainIndex().transferGainIndex()).isNull();
        assertThat(metrics.learningOsKpis().transferGainIndex().insufficientData()).isTrue();
        assertThat(metrics.retention().d1Retention().insufficientData()).isTrue();
        assertThat(metrics.retention().d7Retention().insufficientData()).isTrue();
        assertThat(metrics.retention().d30Retention().insufficientData()).isTrue();
        assertThat(metrics.qualityAndLatency().parseCorrectionRate().insufficientData()).isTrue();
        assertThat(metrics.qualityAndLatency().solveFailureRate().insufficientData()).isTrue();
        assertThat(metrics.qualityAndLatency().solveLatencyP50Ms()).isNull();
        assertThat(metrics.qualityAndLatency().insufficientLatencyData()).isTrue();
        assertThat(metrics.conversionAndUnitEconomics().aiCostPerCompletedSolveUsd()).isNull();
        assertThat(metrics.conversionAndUnitEconomics().aiCostPerPayingActiveLearnerUsd()).isNull();
        assertThat(metrics.conversionAndUnitEconomics().aiCostPerVerifiedLearningSessionUsd()).isNull();
        assertThat(metrics.conversionAndUnitEconomics().byTier().getFirst().insufficientData()).isTrue();
    }
}
