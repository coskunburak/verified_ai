package com.verifiedai.solving.application.secondary;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="app.solving.secondary.worker-enabled",havingValue="true")
final class SecondarySolveWorker {
    private final SecondarySolveService service;
    SecondarySolveWorker(SecondarySolveService service) { this.service=service; }
    @Scheduled(fixedDelayString="${app.solving.secondary.worker-interval:PT5S}")
    void run() { service.runDueJobs(10); }
}
