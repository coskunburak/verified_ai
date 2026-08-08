package com.verifiedai.configuration;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
class SecurityMetrics {
    private final MeterRegistry meterRegistry;

    SecurityMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    void rateLimitDenied(String policy) {
        meterRegistry.counter("security.rate_limit.denied.total", "policy", policy).increment();
    }

    void rateLimitDegradedOpen(String policy) {
        meterRegistry.counter("security.rate_limit.degraded_open.total", "policy", policy).increment();
    }

    void requestRejected(String reason) {
        meterRegistry.counter("security.request.rejected.total", "reason", reason).increment();
    }
}
