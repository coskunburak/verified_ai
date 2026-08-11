package com.verifiedai.problem.api;

import com.verifiedai.problem.application.ProblemSessionProjection;
import java.time.Instant;
import java.util.UUID;

public record ProblemSessionDetailResponse(
    UUID problemSessionId,
    String status,
    String stage,
    String inputMode,
    String nextAction,
    boolean retryable,
    boolean reviewRequired,
    String failureCode,
    ProblemSessionCurrentParseSummaryResponse currentParse,
    ProblemSessionCanonicalSummaryResponse canonicalProblem,
    ProblemSessionClassificationSummaryResponse classification,
    ProblemSessionActiveJobResponse activeJob,
    Instant createdAt,
    Instant updatedAt,
    Instant completedAt,
    long version
) {
    static ProblemSessionDetailResponse from(ProblemSessionProjection projection) {
        return new ProblemSessionDetailResponse(
            projection.sessionId(),
            projection.status(),
            projection.stage().name(),
            projection.inputMode(),
            projection.nextAction().name(),
            projection.retryable(),
            projection.reviewRequired(),
            projection.failureCode(),
            ProblemSessionCurrentParseSummaryResponse.from(projection.currentParse()),
            ProblemSessionCanonicalSummaryResponse.from(projection.canonicalProblem()),
            ProblemSessionClassificationSummaryResponse.from(projection.classification()),
            ProblemSessionActiveJobResponse.from(projection.activeJob()),
            projection.createdAt(),
            projection.updatedAt(),
            projection.completedAt(),
            projection.version()
        );
    }
}
