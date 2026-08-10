package com.verifiedai.problem.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
class ProblemClassificationMetrics {
    private final Counter startedCounter;
    private final Counter successCounter;
    private final Counter reviewRequiredCounter;
    private final Counter unknownCounter;
    private final Counter failureCounter;
    private final Counter schemaInvalidCounter;
    private final Counter ontologyInvalidCounter;
    private final Counter fallbackCounter;
    private final Timer latencyTimer;

    ProblemClassificationMetrics(MeterRegistry meterRegistry) {
        this.startedCounter = Counter.builder("problem.classification.started")
            .description("Classification attempts started")
            .register(meterRegistry);
        this.successCounter = Counter.builder("problem.classification.success")
            .description("Successful classifications")
            .register(meterRegistry);
        this.reviewRequiredCounter = Counter.builder("problem.classification.review_required")
            .description("Classifications requiring review")
            .register(meterRegistry);
        this.unknownCounter = Counter.builder("problem.classification.unknown")
            .description("Unknown classifications")
            .register(meterRegistry);
        this.failureCounter = Counter.builder("problem.classification.failure")
            .description("Classification failures")
            .register(meterRegistry);
        this.schemaInvalidCounter = Counter.builder("problem.classification.schema_invalid")
            .description("Schema-invalid AI responses")
            .register(meterRegistry);
        this.ontologyInvalidCounter = Counter.builder("problem.classification.ontology_invalid")
            .description("Ontology-invalid AI responses")
            .register(meterRegistry);
        this.fallbackCounter = Counter.builder("problem.classification.fallback")
            .description("Fallback provider used")
            .register(meterRegistry);
        this.latencyTimer = Timer.builder("problem.classification.latency")
            .description("Classification total latency")
            .register(meterRegistry);
    }

    void started() { startedCounter.increment(); }
    void success() { successCounter.increment(); }
    void reviewRequired() { reviewRequiredCounter.increment(); }
    void unknown() { unknownCounter.increment(); }
    void failure() { failureCounter.increment(); }
    void schemaInvalid() { schemaInvalidCounter.increment(); }
    void ontologyInvalid() { ontologyInvalidCounter.increment(); }
    void fallback() { fallbackCounter.increment(); }
    void latency(long nanos) { latencyTimer.record(nanos, TimeUnit.NANOSECONDS); }
}
