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
