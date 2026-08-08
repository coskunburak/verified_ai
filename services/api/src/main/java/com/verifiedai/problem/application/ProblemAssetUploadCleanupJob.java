package com.verifiedai.problem.application;

import com.verifiedai.sharedkernel.error.ApiProblemException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class ProblemAssetUploadCleanupJob {
    private final ProblemAssetUploadApplicationService uploadApplicationService;

    ProblemAssetUploadCleanupJob(ProblemAssetUploadApplicationService uploadApplicationService) {
        this.uploadApplicationService = uploadApplicationService;
    }

    @Scheduled(fixedDelayString = "${app.problem-assets.cleanup-interval:PT1H}")
    void expirePendingUploads() {
        try {
            uploadApplicationService.expirePendingUploads(100);
        } catch (ApiProblemException exception) {
            // Storage outage is surfaced by metrics and retrying this bounded job is safe.
        }
    }
}
