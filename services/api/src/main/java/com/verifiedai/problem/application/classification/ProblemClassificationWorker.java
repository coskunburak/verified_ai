package com.verifiedai.problem.application.classification;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class ProblemClassificationWorker {

    private final ProblemClassificationApplicationService
        applicationService;

    ProblemClassificationWorker(
        ProblemClassificationApplicationService
            applicationService
    ) {
        this.applicationService =
            applicationService;
    }

    @Scheduled(
        fixedDelayString =
            "${app.problem-classifier.worker-interval:PT5S}"
    )
    void runDueClassificationJobs() {
        applicationService
            .runDueClassificationJobs(10);
    }
}
