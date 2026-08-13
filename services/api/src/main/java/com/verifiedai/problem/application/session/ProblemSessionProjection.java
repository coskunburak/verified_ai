package com.verifiedai.problem.application.session;

import com.verifiedai.problem.domain.model.session.ProblemSessionNextAction;
import com.verifiedai.problem.domain.model.session.ProblemSessionStage;
import java.time.Instant;
import java.util.UUID;

public record ProblemSessionProjection(
    UUID sessionId,
    String status,
    ProblemSessionStage stage,
    String inputMode,
    ProblemSessionNextAction nextAction,
    boolean retryable,
    boolean reviewRequired,
    String failureCode,
    ProblemSessionCurrentParseSummary currentParse,
    ProblemSessionCanonicalSummary canonicalProblem,
    ProblemSessionClassificationSummary classification,
    ProblemSessionActiveJob activeJob,
    Instant createdAt,
    Instant updatedAt,
    Instant completedAt,
    long version
) {
}
