package com.verifiedai.billing.application;

import java.util.Objects;

/** Result of evaluating and atomically reserving a Learning OS usage quota slot. */
public record QuotaConsumptionDecision(
    boolean allowed,
    boolean idempotentReplay,
    boolean deterministicExempt,
    boolean costCircuitBreakerTripped,
    boolean degradedToDeterministicOnly,
    String reason,
    QuotaSummary summary
) {
    public QuotaConsumptionDecision {
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
    }

    public static QuotaConsumptionDecision allowed(boolean idempotentReplay, QuotaSummary summary) {
        return new QuotaConsumptionDecision(
            true,
            idempotentReplay,
            false,
            summary.costCircuitBreakerTripped(),
            summary.degradedToDeterministicOnly(),
            idempotentReplay ? "IDEMPOTENT_REPLAY" : "ALLOWED",
            summary
        );
    }

    public static QuotaConsumptionDecision allowedDisabled(QuotaSummary summary) {
        return new QuotaConsumptionDecision(
            true,
            false,
            false,
            false,
            false,
            "QUOTA_LEDGER_DISABLED",
            summary
        );
    }

    public static QuotaConsumptionDecision deterministicExempt(QuotaSummary summary) {
        return new QuotaConsumptionDecision(
            true,
            false,
            true,
            summary.costCircuitBreakerTripped(),
            summary.degradedToDeterministicOnly(),
            "DETERMINISTIC_EXEMPT",
            summary
        );
    }

    public static QuotaConsumptionDecision degradedDeterministicFallback(
        boolean costCircuitBreakerTripped,
        String reason,
        QuotaSummary summary
    ) {
        return new QuotaConsumptionDecision(
            false,
            false,
            false,
            costCircuitBreakerTripped,
            true,
            reason,
            summary
        );
    }
}
