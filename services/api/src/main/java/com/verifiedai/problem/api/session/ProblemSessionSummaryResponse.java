package com.verifiedai.problem.api.session;

import com.verifiedai.problem.application.session.ProblemSessionSummary;
import java.time.Instant;
import java.util.UUID;

public record ProblemSessionSummaryResponse(
    UUID problemSessionId,
    String status,
    String stage,
    String inputMode,
    String nextAction,
    boolean retryable,
    boolean reviewRequired,
    Integer currentParseRevision,
    String currentParseSource,
    String classificationStatus,
    String primarySkillId,
    String difficulty,
    Instant createdAt,
    Instant updatedAt,
    Instant completedAt
) {
    static ProblemSessionSummaryResponse from(ProblemSessionSummary summary) {
        return new ProblemSessionSummaryResponse(
            summary.sessionId(),
            summary.status(),
            summary.stage().name(),
            summary.inputMode(),
            summary.nextAction().name(),
            summary.retryable(),
            summary.reviewRequired(),
            summary.currentParseRevision(),
            summary.currentParseSource(),
            summary.classificationStatus(),
            summary.primarySkillId(),
            summary.difficulty(),
            summary.createdAt(),
            summary.updatedAt(),
            summary.completedAt()
        );
    }
}
