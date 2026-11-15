package com.verifiedai.exam.domain;

import java.math.BigDecimal;

public record AppliedAccommodationSnapshot(
        BigDecimal timeMultiplier,
        boolean highContrastMode,
        boolean screenReaderOptimized,
        boolean extendedBreakPrompts
) {
    public AppliedAccommodationSnapshot {
        timeMultiplier = timeMultiplier == null ? new BigDecimal("1.00") : timeMultiplier;
    }

    public static AppliedAccommodationSnapshot standardDefaults() {
        return new AppliedAccommodationSnapshot(new BigDecimal("1.00"), false, false, false);
    }
}
