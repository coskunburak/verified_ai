package com.verifiedai.exam.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Section-level 90% credible interval net breakdown and psychometric telemetry.
 */
public record SectionReadinessBreakdown(
        String sectionCode,
        String sectionTitleKey,
        int questionCount,
        BigDecimal estimatedNetLow,
        BigDecimal estimatedNetMid,
        BigDecimal estimatedNetHigh,
        BigDecimal meanMastery,
        BigDecimal meanRetention,
        BigDecimal epistemicConfidence
) {
    public SectionReadinessBreakdown {
        if (sectionCode == null || sectionCode.isBlank()) {
            throw new IllegalArgumentException("sectionCode must not be blank");
        }
        if (sectionTitleKey == null || sectionTitleKey.isBlank()) {
            throw new IllegalArgumentException("sectionTitleKey must not be blank");
        }
        if (questionCount <= 0) {
            throw new IllegalArgumentException("questionCount must be > 0");
        }
        estimatedNetLow = Objects.requireNonNull(estimatedNetLow, "estimatedNetLow is required")
                .setScale(2, RoundingMode.HALF_UP);
        estimatedNetMid = Objects.requireNonNull(estimatedNetMid, "estimatedNetMid is required")
                .setScale(2, RoundingMode.HALF_UP);
        estimatedNetHigh = Objects.requireNonNull(estimatedNetHigh, "estimatedNetHigh is required")
                .setScale(2, RoundingMode.HALF_UP);
        if (estimatedNetLow.compareTo(estimatedNetMid) > 0
                || estimatedNetMid.compareTo(estimatedNetHigh) > 0) {
            throw new IllegalArgumentException("Section credible interval must satisfy low <= mid <= high");
        }
        meanMastery = normalizeUnit(meanMastery, "meanMastery");
        meanRetention = normalizeUnit(meanRetention, "meanRetention");
        epistemicConfidence = normalizeUnit(epistemicConfidence, "epistemicConfidence");
        sectionCode = sectionCode.trim();
        sectionTitleKey = sectionTitleKey.trim();
    }

    private static BigDecimal normalizeUnit(BigDecimal value, String field) {
        BigDecimal scaled = Objects.requireNonNull(value, field + " is required")
                .setScale(4, RoundingMode.HALF_UP);
        if (scaled.compareTo(BigDecimal.ZERO) < 0 || scaled.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(field + " must be in [0.0000, 1.0000]");
        }
        return scaled;
    }
}
