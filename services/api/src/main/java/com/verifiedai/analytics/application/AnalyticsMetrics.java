package com.verifiedai.analytics.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Low-cardinality Micrometer metrics for analytics event ingestion and product-metrics queries.
 */
@Component
public class AnalyticsMetrics {

    private static final Set<String> BOUNDED_TIERS = Set.of(
        "ALL", "FREE", "PRO", "COACH_PRO", "YKS_SEASON_PASS", "UNKNOWN"
    );

    private final MeterRegistry meterRegistry;

    public AnalyticsMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
    }

    public void recordIngested(String domain, String source, String outcome) {
        Counter.builder("verifiedai.analytics.event.ingested.total")
            .tag("domain", safeTag(domain))
            .tag("source", safeTag(source))
            .tag("outcome", safeTag(outcome))
            .register(meterRegistry)
            .increment();
    }

    public void recordRejected(String reason) {
        Counter.builder("verifiedai.analytics.event.rejected.total")
            .tag("reason", safeTag(reason))
            .register(meterRegistry)
            .increment();
    }

    public void recordMetricsQueryDuration(String tier, Duration duration) {
        String boundedTier = (tier != null && BOUNDED_TIERS.contains(tier)) ? tier : "UNKNOWN";
        Timer.builder("verifiedai.analytics.metrics.query.duration")
            .tag("tier", boundedTier)
            .register(meterRegistry)
            .record(duration);
    }

    private static String safeTag(String value) {
        return (value == null || value.isBlank()) ? "UNKNOWN" : value;
    }
}
