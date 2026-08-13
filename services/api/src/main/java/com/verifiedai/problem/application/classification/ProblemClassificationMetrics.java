package com.verifiedai.problem.application.classification;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
class ProblemClassificationMetrics {

    private final MeterRegistry meterRegistry;

    ProblemClassificationMetrics(
        MeterRegistry meterRegistry
    ) {
        this.meterRegistry = meterRegistry;
    }

    void started() {
        counter(
            "ai.problem.classification.started.total"
        ).increment();
    }

    void success(
        String provider,
        String status,
        String source
    ) {
        counter(
            "ai.problem.classification.success.total",
            "provider",
            provider,
            "status",
            status,
            "source",
            source
        ).increment();
    }

    void failure(String failureClass) {
        counter(
            "ai.problem.classification.failure.total",
            "failure_class",
            failureClass
        ).increment();
    }

    void schemaInvalid() {
        counter(
            "ai.problem.classification.schema_invalid.total"
        ).increment();
    }

    void semanticInvalid() {
        counter(
            "ai.problem.classification.semantic_invalid.total"
        ).increment();
    }

    void ontologyInvalid() {
        counter(
            "ai.problem.classification.ontology_invalid.total"
        ).increment();
    }

    void candidateInvalid() {
        counter(
            "ai.problem.classification.candidate_invalid.total"
        ).increment();
    }

    void fallback() {
        counter(
            "ai.problem.classification.fallback.total"
        ).increment();
    }

    void providerLatency(long millis) {
        timer(
            "ai.problem.classification.provider.duration"
        ).record(
            millis,
            TimeUnit.MILLISECONDS
        );
    }

    void totalLatency(long nanos) {
        timer(
            "ai.problem.classification.total.duration"
        ).record(
            nanos,
            TimeUnit.NANOSECONDS
        );
    }

    void estimatedCost(long micros) {
        DistributionSummary
            .builder(
                "ai.problem.classification.estimated_cost_micros"
            )
            .baseUnit("micro_usd")
            .register(meterRegistry)
            .record(micros);
    }

    private Counter counter(
        String name,
        String... tags
    ) {
        return Counter
            .builder(name)
            .tags(tags)
            .register(meterRegistry);
    }

    private Timer timer(String name) {
        return Timer
            .builder(name)
            .register(meterRegistry);
    }
}
