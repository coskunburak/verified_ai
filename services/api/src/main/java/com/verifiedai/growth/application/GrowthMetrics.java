package com.verifiedai.growth.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Low-cardinality Micrometer counters for Sprint 10.9 organic growth operations.
 * Never attaches user IDs, tokens, referral codes, device hashes, or student content.
 */
@Component
public class GrowthMetrics {

    private final MeterRegistry meterRegistry;

    public GrowthMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordShare(String artifactType, String action) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder("verifiedai.growth.share.total")
            .tag("type", artifactType)
            .tag("action", action)
            .register(meterRegistry)
            .increment();
    }

    public void recordReferral(String outcome) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder("verifiedai.growth.referral.total")
            .tag("outcome", outcome)
            .register(meterRegistry)
            .increment();
    }

    public void recordReviewPrompt(String triggerMoment, String outcome) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder("verifiedai.growth.review_prompt.total")
            .tag("trigger", triggerMoment)
            .tag("outcome", outcome)
            .register(meterRegistry)
            .increment();
    }
}
