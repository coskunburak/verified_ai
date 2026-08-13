package com.verifiedai.problem.application.canonicalization;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
class CanonicalProblemMetrics {
    private final MeterRegistry meterRegistry;

    CanonicalProblemMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    void success(String problemType, String taskType) {
        counter(
            "ai.problem.canonicalization.success.total",
            "problem_type",
            problemType,
            "task_type",
            taskType
        ).increment();
    }

    void failure(CanonicalizationFailure failure) {
        counter("ai.problem.canonicalization.failure.total", "failure", failure.name()).increment();
    }

    void complexityRejected() {
        counter("ai.problem.canonicalization.complexity_rejected.total").increment();
    }

    void latency(long nanos) {
        Timer.builder("ai.problem.canonicalization.duration").register(meterRegistry)
            .record(nanos, TimeUnit.NANOSECONDS);
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(meterRegistry);
    }
}
