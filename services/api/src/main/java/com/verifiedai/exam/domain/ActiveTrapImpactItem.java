package com.verifiedai.exam.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Quantifies expected exam net score loss attributable to an active cognitive mistake trap family.
 */
public record ActiveTrapImpactItem(
        String trapFamilyCode,
        String trapTitleKey,
        BigDecimal posteriorRate,
        BigDecimal estimatedNetLoss,
        int affectedSkillCount
) {
    public ActiveTrapImpactItem {
        if (trapFamilyCode == null || trapFamilyCode.isBlank()) {
            throw new IllegalArgumentException("trapFamilyCode must not be blank");
        }
        if (trapTitleKey == null || trapTitleKey.isBlank()) {
            throw new IllegalArgumentException("trapTitleKey must not be blank");
        }
        posteriorRate = Objects.requireNonNull(posteriorRate, "posteriorRate is required")
                .setScale(4, RoundingMode.HALF_UP);
        if (posteriorRate.compareTo(BigDecimal.ZERO) < 0 || posteriorRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("posteriorRate must be in [0.0000, 1.0000]");
        }
        estimatedNetLoss = Objects.requireNonNull(estimatedNetLoss, "estimatedNetLoss is required")
                .setScale(2, RoundingMode.HALF_UP);
        if (estimatedNetLoss.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("estimatedNetLoss must be non-negative");
        }
        if (affectedSkillCount < 0) {
            throw new IllegalArgumentException("affectedSkillCount must be non-negative");
        }
        trapFamilyCode = trapFamilyCode.trim();
        trapTitleKey = trapTitleKey.trim();
    }
}
