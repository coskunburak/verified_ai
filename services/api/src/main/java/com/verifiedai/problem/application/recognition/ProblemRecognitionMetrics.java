package com.verifiedai.problem.application.recognition;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
class ProblemRecognitionMetrics {
    private final MeterRegistry meterRegistry;

    ProblemRecognitionMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    void started() {
        counter("ai.vision.recognition.started.total").increment();
    }

    void success(String provider, boolean reviewRequired) {
        counter("ai.vision.recognition.success.total", "provider", provider, "review_required", Boolean.toString(reviewRequired)).increment();
    }

    void failure(String failureClass) {
        counter("ai.vision.recognition.failure.total", "failure_class", failureClass).increment();
    }

    void timeout() {
        counter("ai.vision.recognition.timeout.total").increment();
    }

    void schemaInvalid() {
        counter("ai.vision.recognition.schema_invalid.total").increment();
    }

    void fallback() {
        counter("ai.vision.recognition.fallback.total").increment();
    }

    void providerLatency(long millis) {
        timer("ai.vision.recognition.provider.duration").record(millis, TimeUnit.MILLISECONDS);
    }

    void totalLatency(long nanos) {
        timer("ai.vision.recognition.total.duration").record(nanos, TimeUnit.NANOSECONDS);
    }

    void estimatedCost(long micros) {
        DistributionSummary.builder("ai.vision.recognition.estimated_cost_micros")
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
