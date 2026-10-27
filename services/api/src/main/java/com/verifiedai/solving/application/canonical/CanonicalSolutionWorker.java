package com.verifiedai.solving.application.canonical;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="app.solving.canonical.worker-enabled",havingValue="true")
final class CanonicalSolutionWorker {
    private final CanonicalSolutionService service;
    private final MeterRegistry metrics;
    CanonicalSolutionWorker(CanonicalSolutionService service,MeterRegistry metrics) { this.service=service;this.metrics=metrics; }
    @Scheduled(fixedDelayString="${app.solving.canonical.worker-interval:PT5S}")
    void run() {
        try { service.recover(10); }
        catch(RuntimeException e) { metrics.counter("solving.canonical.recovery","outcome","UNAVAILABLE").increment(); }
    }
}
