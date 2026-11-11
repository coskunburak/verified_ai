package com.verifiedai.analytics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record MasteryCalibrationSummaryDto(
    String schemaVersion,
    Instant evaluatedAt,
    String canonicalSkillCodeFilter,
    int windowDays,
    int sampleSize,
    BigDecimal brierScore,
    BigDecimal brierThreshold,
    BigDecimal expectedCalibrationError,
    BigDecimal eceThreshold,
    BigDecimal signedCalibrationBias,
    String calibrationStatus,
    boolean gatePassed,
    List<ReliabilityBinDto> reliabilityBins,
    List<SkillCalibrationSliceDto> skillSlices
) {
    public static final String CURRENT_SCHEMA_VERSION = "1.0.0";
    public static final BigDecimal DEFAULT_BRIER_THRESHOLD = new BigDecimal("0.2");
    public static final BigDecimal DEFAULT_ECE_THRESHOLD = new BigDecimal("0.08");

    public record ReliabilityBinDto(
        int binIndex,
        BigDecimal lowerBound,
        BigDecimal upperBound,
        int sampleCount,
        BigDecimal meanPredictedConfidence,
        BigDecimal observedIndependentAccuracy,
        BigDecimal absoluteCalibrationGap
    ) {}

    public record SkillCalibrationSliceDto(
        String canonicalSkillCode,
        int sampleSize,
        BigDecimal brierScore,
        BigDecimal expectedCalibrationError,
        String calibrationStatus,
        boolean gatePassed
    ) {}
}
