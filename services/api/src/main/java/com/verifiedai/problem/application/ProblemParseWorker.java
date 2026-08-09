package com.verifiedai.problem.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class ProblemParseWorker {
    private final ProblemParseApplicationService parseApplicationService;

    ProblemParseWorker(ProblemParseApplicationService parseApplicationService) {
        this.parseApplicationService = parseApplicationService;
    }

    @Scheduled(fixedDelayString = "${app.problem-parser.worker-interval:PT5S}")
    void runDueParseJobs() {
        parseApplicationService.runDueParseJobs(10);
    }
}
