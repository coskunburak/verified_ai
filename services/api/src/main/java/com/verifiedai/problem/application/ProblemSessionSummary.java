package com.verifiedai.problem.application;

import com.verifiedai.problem.domain.model.ProblemSessionNextAction;
import com.verifiedai.problem.domain.model.ProblemSessionStage;
import java.time.Instant;
import java.util.UUID;

public record ProblemSessionSummary(
    UUID sessionId,
    String status,
    ProblemSessionStage stage,
    String inputMode,
    ProblemSessionNextAction nextAction,
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
    public static ProblemSessionSummary from(ProblemSessionProjection projection) {
        ProblemSessionCurrentParseSummary currentParse = projection.currentParse();
        ProblemSessionClassificationSummary classification = projection.classification();
        return new ProblemSessionSummary(
            projection.sessionId(),
            projection.status(),
            projection.stage(),
            projection.inputMode(),
            projection.nextAction(),
            projection.retryable(),
            projection.reviewRequired(),
            currentParse == null ? null : currentParse.revision(),
            currentParse == null ? null : currentParse.source(),
            classification == null ? null : classification.status(),
            classification == null ? null : classification.primarySkillId(),
            classification == null ? null : classification.difficulty(),
            projection.createdAt(),
            projection.updatedAt(),
            projection.completedAt()
        );
    }
}
