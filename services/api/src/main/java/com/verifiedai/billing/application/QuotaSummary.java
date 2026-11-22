package com.verifiedai.billing.application;

import java.time.Instant;
import java.util.Objects;

/** Additive CNT-LOS-12 quota snapshot returned on EntitlementV2 without exposing internal cost figures. */
public record QuotaSummary(
    String periodKey,
    int aiVerificationsUsed,
    int aiVerificationsLimit,
    int remainingAiChecks,
    int hybridTwinsUsed,
    int hybridTwinsLimit,
    boolean deterministicTwinsUnlimited,
    boolean costCircuitBreakerTripped,
    boolean degradedToDeterministicOnly,
    Instant resetsAt
) {
    public QuotaSummary {
        Objects.requireNonNull(periodKey, "periodKey must not be null");
        Objects.requireNonNull(resetsAt, "resetsAt must not be null");
        if (aiVerificationsUsed < 0 || aiVerificationsLimit < 0 || remainingAiChecks < 0
            || hybridTwinsUsed < 0 || hybridTwinsLimit < 0) {
            throw new IllegalArgumentException("Quota counts must be non-negative");
        }
        if (!deterministicTwinsUnlimited) {
            throw new IllegalArgumentException("INV-QUOTA-001 requires deterministicTwinsUnlimited to remain true");
        }
    }

    public static QuotaSummary of(
        String periodKey,
        int aiVerificationsUsed,
        int aiVerificationsLimit,
        int hybridTwinsUsed,
        int hybridTwinsLimit,
        boolean costCircuitBreakerTripped,
        Instant resetsAt
    ) {
        int remaining = costCircuitBreakerTripped ? 0 : Math.max(0, aiVerificationsLimit - aiVerificationsUsed);
        boolean degraded = costCircuitBreakerTripped || remaining == 0;
        return new QuotaSummary(
            periodKey,
            aiVerificationsUsed,
            aiVerificationsLimit,
            remaining,
            hybridTwinsUsed,
            hybridTwinsLimit,
            true,
            costCircuitBreakerTripped,
            degraded,
            resetsAt
        );
    }
}
