package com.verifiedai.problem.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
class ProblemParseCorrectionMetrics {
    private final MeterRegistry meterRegistry;

    ProblemParseCorrectionMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    void request() {
        counter("problem.parse.correction.request.total").increment();
    }

    void success(String reason) {
        counter("problem.parse.correction.success.total", "reason", lowCardinality(reason)).increment();
    }

    void failure(String outcome) {
        counter("problem.parse.correction.failure.total", "outcome", outcome).increment();
    }

    void conflict() {
        counter("problem.parse.correction.conflict.total").increment();
    }

    void idempotentReplay() {
        counter("problem.parse.correction.idempotent_replay.total").increment();
    }

    void invalid(String failure) {
        counter("problem.parse.correction.invalid.total", "failure", failure).increment();
    }

    void selectionChanged(String source) {
        counter("problem.parse.selection.changed.total", "source", source).increment();
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(meterRegistry);
    }

    private static String lowCardinality(String reason) {
        return reason == null || reason.isBlank() ? "UNSPECIFIED" : reason;
    }
}
