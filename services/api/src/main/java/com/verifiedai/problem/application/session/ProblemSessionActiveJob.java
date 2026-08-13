package com.verifiedai.problem.application.session;

import com.verifiedai.problem.domain.model.session.ProblemSessionActiveJobType;
import java.util.UUID;

public record ProblemSessionActiveJob(
    ProblemSessionActiveJobType type,
    UUID id,
    String status,
    int attemptCount,
    int maxAttempts,
    String failureCode
) {
}
