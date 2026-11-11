package com.verifiedai.analytics;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic psychometric calibration engine evaluating Brier score (<= 0.20)
 * and 10-bin Expected Calibration Error (ECE <= 0.08) for unassisted retention predictions (G-PED-05).
 * Uses BigDecimal fixed-point arithmetic with MathContext.DECIMAL64 to prevent IEEE 754 drift.
 */
public final class BrierAndEceCalculator {

    public static final BigDecimal BRIER_GATE_THRESHOLD = new BigDecimal("0.20000");
    public static final BigDecimal ECE_GATE_THRESHOLD = new BigDecimal("0.08000");
    public static final BigDecimal MIN_PREDICTED_PROB = new BigDecimal("0.0500");
    public static final BigDecimal MAX_PREDICTED_PROB = new BigDecimal("0.9900");
    public static final int BIN_COUNT = 10;

    private static final BigDecimal PROB_ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal METRIC_ZERO = BigDecimal.ZERO.setScale(5, RoundingMode.HALF_UP);
    private static final BigDecimal BIN_NINE_LOWER = new BigDecimal("0.9000");

    private BrierAndEceCalculator() {
    }

    public enum CalibrationStatus {
        CALIBRATED,
        OVERCONFIDENT,
        UNDERCONFIDENT
    }

    public record CalibrationSample(BigDecimal predictedRetentionProb, int observedOutcome) {
        public CalibrationSample {
            Objects.requireNonNull(predictedRetentionProb, "predictedRetentionProb cannot be null");
            if (predictedRetentionProb.compareTo(MIN_PREDICTED_PROB) < 0 || predictedRetentionProb.compareTo(MAX_PREDICTED_PROB) > 0) {
                throw new IllegalArgumentException("predictedRetentionProb out of bounds [0.0500, 0.9900]: " + predictedRetentionProb);
            }
            if (observedOutcome != 0 && observedOutcome != 1) {
                throw new IllegalArgumentException("observedOutcome must be 0 or 1: " + observedOutcome);
            }
        }
    }

    public record ReliabilityBinResult(
        int binIndex,
        BigDecimal lowerBound,
        BigDecimal upperBound,
        int sampleCount,
        BigDecimal meanPredictedConfidence,
        BigDecimal observedIndependentAccuracy,
        BigDecimal absoluteCalibrationGap
    ) {}

    public record CalibrationEvaluationResult(
        int sampleSize,
        BigDecimal brierScore,
        BigDecimal expectedCalibrationError,
        BigDecimal signedCalibrationBias,
        CalibrationStatus calibrationStatus,
        boolean gatePassed,
        List<ReliabilityBinResult> reliabilityBins
    ) {}

    /**
     * Computes the single-attempt Brier residual (p - y)^2 scaled to 5 decimal places.
     */
    public static BigDecimal computeBrierResidual(BigDecimal predictedRetentionProb, int observedOutcome) {
        Objects.requireNonNull(predictedRetentionProb, "predictedRetentionProb cannot be null");
        if (predictedRetentionProb.compareTo(MIN_PREDICTED_PROB) < 0 || predictedRetentionProb.compareTo(MAX_PREDICTED_PROB) > 0) {
            throw new IllegalArgumentException("predictedRetentionProb out of bounds [0.0500, 0.9900]: " + predictedRetentionProb);
        }
        if (observedOutcome != 0 && observedOutcome != 1) {
            throw new IllegalArgumentException("observedOutcome must be 0 or 1: " + observedOutcome);
        }
        BigDecimal diff = predictedRetentionProb.subtract(BigDecimal.valueOf(observedOutcome), MathContext.DECIMAL64);
        return diff.multiply(diff, MathContext.DECIMAL64).setScale(5, RoundingMode.HALF_UP);
    }

    /**
     * Resolves the 1-based bin index for the given retention probability.
     * Bins 1..9 use half-open intervals [(b-1)/10, b/10), and Bin 10 uses closed interval [0.9000, 1.0000].
     */
    public static int resolveBinIndex(BigDecimal predictedRetentionProb) {
        Objects.requireNonNull(predictedRetentionProb, "predictedRetentionProb cannot be null");
        if (predictedRetentionProb.compareTo(BigDecimal.ZERO) < 0 || predictedRetentionProb.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("predictedRetentionProb must be between 0.0 and 1.0: " + predictedRetentionProb);
        }
        if (predictedRetentionProb.compareTo(BIN_NINE_LOWER) >= 0) {
            return 10;
        }
        BigDecimal scaled = predictedRetentionProb.multiply(BigDecimal.TEN, MathContext.DECIMAL64);
        int bin = scaled.intValue() + 1;
        return Math.min(Math.max(bin, 1), 9);
    }

    /**
     * Deterministically evaluates calibration metrics across a collection of unassisted observations.
     */
    public static CalibrationEvaluationResult evaluate(List<CalibrationSample> samples) {
        if (samples == null || samples.isEmpty()) {
            List<ReliabilityBinResult> emptyBins = new ArrayList<>(BIN_COUNT);
            for (int b = 1; b <= BIN_COUNT; b++) {
                BigDecimal lower = BigDecimal.valueOf(b - 1).divide(BigDecimal.TEN, 4, RoundingMode.HALF_UP);
                BigDecimal upper = BigDecimal.valueOf(b).divide(BigDecimal.TEN, 4, RoundingMode.HALF_UP);
                emptyBins.add(new ReliabilityBinResult(b, lower, upper, 0, PROB_ZERO, PROB_ZERO, PROB_ZERO));
            }
            return new CalibrationEvaluationResult(
                0,
                METRIC_ZERO,
                METRIC_ZERO,
                METRIC_ZERO,
                CalibrationStatus.CALIBRATED,
                true,
                Collections.unmodifiableList(emptyBins)
            );
        }

        int n = samples.size();
        BigDecimal bigN = BigDecimal.valueOf(n);

        BigDecimal sumBrier = BigDecimal.ZERO;
        BigDecimal sumBias = BigDecimal.ZERO;

        @SuppressWarnings("unchecked")
        List<CalibrationSample>[] binBuckets = new List[BIN_COUNT + 1];
        for (int b = 1; b <= BIN_COUNT; b++) {
            binBuckets[b] = new ArrayList<>();
        }

        for (CalibrationSample s : samples) {
            BigDecimal p = s.predictedRetentionProb();
            BigDecimal y = BigDecimal.valueOf(s.observedOutcome());

            BigDecimal diff = p.subtract(y, MathContext.DECIMAL64);
            BigDecimal sqDiff = diff.multiply(diff, MathContext.DECIMAL64);

            sumBrier = sumBrier.add(sqDiff, MathContext.DECIMAL64);
            sumBias = sumBias.add(diff, MathContext.DECIMAL64);

            int bin = resolveBinIndex(p);
            binBuckets[bin].add(s);
        }

        BigDecimal brierScore = sumBrier.divide(bigN, MathContext.DECIMAL64).setScale(5, RoundingMode.HALF_UP);
        BigDecimal signedCalibrationBias = sumBias.divide(bigN, MathContext.DECIMAL64).setScale(5, RoundingMode.HALF_UP);

        BigDecimal eceSum = BigDecimal.ZERO;
        List<ReliabilityBinResult> bins = new ArrayList<>(BIN_COUNT);

        for (int b = 1; b <= BIN_COUNT; b++) {
            BigDecimal lower = BigDecimal.valueOf(b - 1).divide(BigDecimal.TEN, 4, RoundingMode.HALF_UP);
            BigDecimal upper = BigDecimal.valueOf(b).divide(BigDecimal.TEN, 4, RoundingMode.HALF_UP);
            List<CalibrationSample> bucket = binBuckets[b];
            int count = bucket.size();

            if (count == 0) {
                bins.add(new ReliabilityBinResult(b, lower, upper, 0, PROB_ZERO, PROB_ZERO, PROB_ZERO));
            } else {
                BigDecimal bigCount = BigDecimal.valueOf(count);
                BigDecimal sumP = BigDecimal.ZERO;
                BigDecimal sumY = BigDecimal.ZERO;

                for (CalibrationSample s : bucket) {
                    sumP = sumP.add(s.predictedRetentionProb(), MathContext.DECIMAL64);
                    sumY = sumY.add(BigDecimal.valueOf(s.observedOutcome()), MathContext.DECIMAL64);
                }

                BigDecimal meanP = sumP.divide(bigCount, 4, RoundingMode.HALF_UP);
                BigDecimal accY = sumY.divide(bigCount, 4, RoundingMode.HALF_UP);
                BigDecimal gap = accY.subtract(meanP, MathContext.DECIMAL64).abs().setScale(4, RoundingMode.HALF_UP);

                BigDecimal binWeight = bigCount.divide(bigN, MathContext.DECIMAL64);
                BigDecimal weightedGap = binWeight.multiply(gap, MathContext.DECIMAL64);
                eceSum = eceSum.add(weightedGap, MathContext.DECIMAL64);

                bins.add(new ReliabilityBinResult(b, lower, upper, count, meanP, accY, gap));
            }
        }

        BigDecimal expectedCalibrationError = eceSum.setScale(5, RoundingMode.HALF_UP);

        boolean gatePassed = brierScore.compareTo(BRIER_GATE_THRESHOLD) <= 0
            && expectedCalibrationError.compareTo(ECE_GATE_THRESHOLD) <= 0;

        CalibrationStatus status;
        if (gatePassed) {
            status = CalibrationStatus.CALIBRATED;
        } else if (signedCalibrationBias.compareTo(BigDecimal.ZERO) > 0) {
            status = CalibrationStatus.OVERCONFIDENT;
        } else {
            status = CalibrationStatus.UNDERCONFIDENT;
        }

        return new CalibrationEvaluationResult(
            n,
            brierScore,
            expectedCalibrationError,
            signedCalibrationBias,
            status,
            gatePassed,
            Collections.unmodifiableList(bins)
        );
    }
}
