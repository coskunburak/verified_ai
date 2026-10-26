package com.verifiedai.solving.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="app.solving.worker-enabled",havingValue="true")
final class PrimarySolveWorker {
    private final PrimarySolveService service;
    PrimarySolveWorker(PrimarySolveService service) { this.service=service; }
    @Scheduled(fixedDelayString="${app.solving.worker-interval:PT5S}")
    void run() { service.runDueJobs(10); }
}
