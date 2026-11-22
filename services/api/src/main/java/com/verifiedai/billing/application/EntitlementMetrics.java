package com.verifiedai.billing.application;

import com.verifiedai.billing.domain.model.EntitlementStatus;
import com.verifiedai.billing.domain.model.EntitlementTier;
import com.verifiedai.billing.domain.model.PremiumCapability;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
class EntitlementMetrics {
    private final MeterRegistry meterRegistry;

    EntitlementMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    void checked(LearningOsCapability capability, String tier, boolean allowed, String denyReason) {
        meterRegistry.counter("entitlement_checked",
            "capability", capability.name(),
            "effective_tier", tier,
            "outcome", allowed ? "ALLOW" : "DENY",
            "deny_reason", denyReason
        ).increment();
    }

    private int rank(String value) {
        return switch (value) { case "FREE" -> 0; case "PRO" -> 1; default -> 2; };
    }

    void transition(String before, String after, String reason) {
        int direction = Integer.compare(rank(after), rank(before));
        if (direction == 0) return;
        Runnable emit = () -> meterRegistry.counter(
            direction > 0 ? "entitlement_upgraded" : "entitlement_downgraded",
            "transition_reason", reason
        ).increment();
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() { emit.run(); }
                });
        } else {
            emit.run();
        }
    }

    void resolution(EntitlementTier tier, EntitlementStatus status) {
        Counter.builder("entitlement.resolution.total")
            .tag("tier", tier.name())
            .tag("status", status.name())
            .register(meterRegistry)
            .increment();
    }

    void accessAllowed(PremiumCapability capability, EntitlementTier tier) {
        accessCounter("entitlement.access.allowed.total", capability, tier).increment();
    }

    void accessDenied(PremiumCapability capability, EntitlementTier tier) {
        accessCounter("entitlement.access.denied.total", capability, tier).increment();
    }

    private Counter accessCounter(String name, PremiumCapability capability, EntitlementTier tier) {
        return Counter.builder(name)
            .tag("capability", capability.name())
            .tag("tier", tier.name())
            .register(meterRegistry);
    }
}
