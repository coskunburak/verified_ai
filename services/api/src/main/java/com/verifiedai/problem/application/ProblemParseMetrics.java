package com.verifiedai.problem.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
class ProblemParseMetrics {
    private final MeterRegistry meterRegistry;

    ProblemParseMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    void started() {
        counter("ai.problem.parse.started.total").increment();
    }

    void success(String provider, String supportStatus, boolean reviewRequired) {
        counter(
            "ai.problem.parse.success.total",
            "provider",
            provider,
            "support_status",
            supportStatus,
            "review_required",
            Boolean.toString(reviewRequired)
        ).increment();
    }

    void unsupported(String reason) {
        counter("ai.problem.parse.unsupported.total", "reason", reason).increment();
    }

    void failure(String failureClass) {
        counter("ai.problem.parse.failure.total", "failure_class", failureClass).increment();
    }

    void schemaInvalid() {
        counter("ai.problem.parse.schema_invalid.total").increment();
    }

    void semanticInvalid() {
        counter("ai.problem.parse.semantic_invalid.total").increment();
    }

    void fallback() {
        counter("ai.problem.parse.fallback.total").increment();
    }

    void providerLatency(long millis) {
        timer("ai.problem.parse.provider.duration").record(millis, TimeUnit.MILLISECONDS);
    }

    void totalLatency(long nanos) {
        timer("ai.problem.parse.total.duration").record(nanos, TimeUnit.NANOSECONDS);
    }

    void estimatedCost(long micros) {
        DistributionSummary.builder("ai.problem.parse.estimated_cost_micros")
            .baseUnit("micro_usd")
            .register(meterRegistry)
            .record(micros);
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(meterRegistry);
    }

    private Timer timer(String name) {
        return Timer.builder(name).register(meterRegistry);
    }
}
