package com.verifiedai.problem.api;

import com.verifiedai.problem.application.ProblemSessionActiveJob;
import java.util.UUID;

public record ProblemSessionActiveJobResponse(
    String type,
    UUID jobId,
    String status,
    int attemptCount,
    int maxAttempts,
    String failureCode
) {
    static ProblemSessionActiveJobResponse from(ProblemSessionActiveJob activeJob) {
        if (activeJob == null) {
            return null;
        }
        return new ProblemSessionActiveJobResponse(
            activeJob.type().name(),
            activeJob.id(),
            activeJob.status(),
            activeJob.attemptCount(),
            activeJob.maxAttempts(),
            activeJob.failureCode()
        );
    }
}
