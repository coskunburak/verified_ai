package com.verifiedai.problem.application.asset;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
class ProblemAssetPreprocessingMetrics {
    private final MeterRegistry meterRegistry;

    ProblemAssetPreprocessingMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    void started() {
        counter("problem_asset.preprocessing.started.total").increment();
    }

    void success(String qualityOutcome) {
        counter("problem_asset.preprocessing.completed.total", "outcome", qualityOutcome).increment();
    }

    void failed(String failureCode) {
        counter("problem_asset.preprocessing.failed.total", "failure_code", failureCode).increment();
    }

    void warning(String signalType) {
        counter("problem_asset.preprocessing.quality_warning.total", "signal_type", signalType).increment();
    }

    void derivativeGenerated(String kind) {
        counter("problem_asset.preprocessing.derivative.generated.total", "kind", kind).increment();
    }

    void latency(long nanos) {
        timer("problem_asset.preprocessing.duration").record(nanos, TimeUnit.NANOSECONDS);
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(meterRegistry);
    }

    private Timer timer(String name) {
        return Timer.builder(name).register(meterRegistry);
    }
}
