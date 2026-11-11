package com.verifiedai.analytics;

import com.verifiedai.analytics.BrierAndEceCalculator.CalibrationEvaluationResult;
import com.verifiedai.analytics.BrierAndEceCalculator.CalibrationSample;
import com.verifiedai.analytics.BrierAndEceCalculator.CalibrationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrierAndEceCalculatorTest {

    @Test
    @DisplayName("Empty cohort produces 10 zeroed reliability bins and passes gate")
    void emptyCohortEvaluatesSafely() {
        CalibrationEvaluationResult result = BrierAndEceCalculator.evaluate(List.of());

        assertThat(result.sampleSize()).isZero();
        assertThat(result.brierScore()).isEqualTo(new BigDecimal("0.00000"));
        assertThat(result.expectedCalibrationError()).isEqualTo(new BigDecimal("0.00000"));
        assertThat(result.signedCalibrationBias()).isEqualTo(new BigDecimal("0.00000"));
        assertThat(result.calibrationStatus()).isEqualTo(CalibrationStatus.CALIBRATED);
        assertThat(result.gatePassed()).isTrue();
        assertThat(result.reliabilityBins()).hasSize(10);

        for (int i = 0; i < 10; i++) {
            var bin = result.reliabilityBins().get(i);
            assertThat(bin.binIndex()).isEqualTo(i + 1);
            assertThat(bin.sampleCount()).isZero();
            assertThat(bin.meanPredictedConfidence()).isEqualTo(new BigDecimal("0.0000"));
            assertThat(bin.observedIndependentAccuracy()).isEqualTo(new BigDecimal("0.0000"));
            assertThat(bin.absoluteCalibrationGap()).isEqualTo(new BigDecimal("0.0000"));
        }
    }

    @Test
    @DisplayName("10-bin boundary mapping strictly enforces half-open [0, 0.9) and closed [0.9, 1.0]")
    void resolveBinIndexBoundaries() {
        assertThat(BrierAndEceCalculator.resolveBinIndex(new BigDecimal("0.0000"))).isEqualTo(1);
        assertThat(BrierAndEceCalculator.resolveBinIndex(new BigDecimal("0.0500"))).isEqualTo(1);
        assertThat(BrierAndEceCalculator.resolveBinIndex(new BigDecimal("0.0999"))).isEqualTo(1);

        assertThat(BrierAndEceCalculator.resolveBinIndex(new BigDecimal("0.1000"))).isEqualTo(2);
        assertThat(BrierAndEceCalculator.resolveBinIndex(new BigDecimal("0.1999"))).isEqualTo(2);

        assertThat(BrierAndEceCalculator.resolveBinIndex(new BigDecimal("0.8000"))).isEqualTo(9);
        assertThat(BrierAndEceCalculator.resolveBinIndex(new BigDecimal("0.8999"))).isEqualTo(9);

        // Bin 10 is closed on both lower and upper bounds
        assertThat(BrierAndEceCalculator.resolveBinIndex(new BigDecimal("0.9000"))).isEqualTo(10);
        assertThat(BrierAndEceCalculator.resolveBinIndex(new BigDecimal("0.9500"))).isEqualTo(10);
        assertThat(BrierAndEceCalculator.resolveBinIndex(new BigDecimal("0.9900"))).isEqualTo(10);
        assertThat(BrierAndEceCalculator.resolveBinIndex(new BigDecimal("1.0000"))).isEqualTo(10);

        assertThatThrownBy(() -> BrierAndEceCalculator.resolveBinIndex(new BigDecimal("-0.0001")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BrierAndEceCalculator.resolveBinIndex(new BigDecimal("1.0001")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("computeBrierResidual validates bounds and computes exact fixed-point square")
    void computeBrierResidualPrecision() {
        // (0.8000 - 1)^2 = (-0.2000)^2 = 0.04000
        BigDecimal res1 = BrierAndEceCalculator.computeBrierResidual(new BigDecimal("0.8000"), 1);
        assertThat(res1).isEqualTo(new BigDecimal("0.04000"));

        // (0.7500 - 0)^2 = 0.56250
        BigDecimal res2 = BrierAndEceCalculator.computeBrierResidual(new BigDecimal("0.7500"), 0);
        assertThat(res2).isEqualTo(new BigDecimal("0.56250"));

        // Out of bounds checks
        assertThatThrownBy(() -> BrierAndEceCalculator.computeBrierResidual(new BigDecimal("0.0499"), 1))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BrierAndEceCalculator.computeBrierResidual(new BigDecimal("0.9901"), 1))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BrierAndEceCalculator.computeBrierResidual(new BigDecimal("0.5000"), 2))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Deterministic evaluation detects CALIBRATED regime (BS <= 0.20, ECE <= 0.08)")
    void calibratedRegimeDetection() {
        // Synthetic well-calibrated cohort:
        // Bin 7: 10 samples with p=0.7000, 7 correct (y=1) -> meanP=0.7000, accY=0.7000, gap=0.0000
        // Bin 8: 10 samples with p=0.8000, 8 correct (y=1) -> meanP=0.8000, accY=0.8000, gap=0.0000
        List<CalibrationSample> samples = new ArrayList<>();
        for (int i = 0; i < 7; i++) samples.add(new CalibrationSample(new BigDecimal("0.7000"), 1));
        for (int i = 0; i < 3; i++) samples.add(new CalibrationSample(new BigDecimal("0.7000"), 0));
        for (int i = 0; i < 8; i++) samples.add(new CalibrationSample(new BigDecimal("0.8000"), 1));
        for (int i = 0; i < 2; i++) samples.add(new CalibrationSample(new BigDecimal("0.8000"), 0));

        CalibrationEvaluationResult result = BrierAndEceCalculator.evaluate(samples);

        assertThat(result.sampleSize()).isEqualTo(20);
        assertThat(result.brierScore().compareTo(BrierAndEceCalculator.BRIER_GATE_THRESHOLD)).isLessThanOrEqualTo(0);
        assertThat(result.expectedCalibrationError().compareTo(BrierAndEceCalculator.ECE_GATE_THRESHOLD)).isLessThanOrEqualTo(0);
        assertThat(result.calibrationStatus()).isEqualTo(CalibrationStatus.CALIBRATED);
        assertThat(result.gatePassed()).isTrue();
    }

    @Test
    @DisplayName("Deterministic evaluation detects OVERCONFIDENT regime when predicted exceeds accuracy")
    void overconfidentRegimeDetection() {
        // High predicted retention (p=0.9000), but observed accuracy is 0 (all y=0)
        List<CalibrationSample> samples = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            samples.add(new CalibrationSample(new BigDecimal("0.9000"), 0));
        }

        CalibrationEvaluationResult result = BrierAndEceCalculator.evaluate(samples);

        assertThat(result.sampleSize()).isEqualTo(20);
        // (0.9 - 0)^2 = 0.81000
        assertThat(result.brierScore()).isEqualTo(new BigDecimal("0.81000"));
        assertThat(result.expectedCalibrationError()).isEqualTo(new BigDecimal("0.90000"));
        assertThat(result.signedCalibrationBias()).isEqualTo(new BigDecimal("0.90000"));
        assertThat(result.gatePassed()).isFalse();
        assertThat(result.calibrationStatus()).isEqualTo(CalibrationStatus.OVERCONFIDENT);
    }

    @Test
    @DisplayName("Deterministic evaluation detects UNDERCONFIDENT regime when accuracy exceeds predicted")
    void underconfidentRegimeDetection() {
        // Low predicted retention (p=0.1000), but observed accuracy is 1 (all y=1)
        List<CalibrationSample> samples = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            samples.add(new CalibrationSample(new BigDecimal("0.1000"), 1));
        }

        CalibrationEvaluationResult result = BrierAndEceCalculator.evaluate(samples);

        assertThat(result.sampleSize()).isEqualTo(20);
        // (0.1 - 1)^2 = 0.81000
        assertThat(result.brierScore()).isEqualTo(new BigDecimal("0.81000"));
        assertThat(result.expectedCalibrationError()).isEqualTo(new BigDecimal("0.90000"));
        assertThat(result.signedCalibrationBias()).isEqualTo(new BigDecimal("-0.90000"));
        assertThat(result.gatePassed()).isFalse();
        assertThat(result.calibrationStatus()).isEqualTo(CalibrationStatus.UNDERCONFIDENT);
    }
}
