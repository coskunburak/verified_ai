package com.verifiedai.problem.application.recognition;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class ProblemRecognitionWorker {
    private final ProblemRecognitionApplicationService recognitionApplicationService;

    ProblemRecognitionWorker(ProblemRecognitionApplicationService recognitionApplicationService) {
        this.recognitionApplicationService = recognitionApplicationService;
    }

    @Scheduled(fixedDelayString = "${app.problem-recognition.worker-interval:PT5S}")
    void runDueRecognitionJobs() {
        recognitionApplicationService.runDueRecognitionJobs(10);
    }
}
