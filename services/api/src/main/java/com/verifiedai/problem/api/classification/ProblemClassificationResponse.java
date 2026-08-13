package com.verifiedai.problem.api.classification;

import com.verifiedai.problem.application.classification.ProblemClassificationStatusResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

record ProblemClassificationResponse(
    UUID classificationJobId,
    UUID problemSessionId,
    UUID canonicalProblemId,
    Integer canonicalProblemRevision,

    String jobStatus,
    String capability,
    int attemptCount,
    int maxAttempts,
    String lastErrorCode,
    String lastFailureClass,

    UUID classificationId,
    Integer classificationRevision,
    String classificationSource,
    String classificationStatus,
    String reviewReason,

    String ontologyVersion,
    String projectionVersion,
    String schemaVersion,

    String subjectId,
    String topicId,
    String primarySkillId,
    List<String> secondarySkillIds,

    String difficulty,
    String difficultyPolicyVersion,

    String confidenceBand,
    String confidencePolicyVersion,
    String confidenceCalibration,

    String provider,
    String model,
    Boolean fallbackUsed,

    Instant createdAt,
    Instant updatedAt,
    Instant completedAt,
    Instant classificationCreatedAt
) {

    ProblemClassificationResponse {
        secondarySkillIds =
            secondarySkillIds == null
                ? List.of()
                : List.copyOf(
                secondarySkillIds
            );
    }

    static ProblemClassificationResponse from(
        ProblemClassificationStatusResult result
    ) {
        return new ProblemClassificationResponse(
            result.classificationJobId(),
            result.problemSessionId(),
            result.canonicalProblemId(),
            result.canonicalProblemRevision(),

            result.jobStatus(),
            result.capability(),
            result.attemptCount(),
            result.maxAttempts(),
            result.lastErrorCode(),
            result.lastFailureClass(),

            result.classificationId(),
            result.classificationRevision(),
            result.classificationSource(),
            result.classificationStatus(),
            result.reviewReason(),

            result.ontologyVersion(),
            result.projectionVersion(),
            result.schemaVersion(),

            result.subjectId(),
            result.topicId(),
            result.primarySkillId(),
            result.secondarySkillIds(),

            result.difficulty(),
            result.difficultyPolicyVersion(),

            result.confidenceBand(),
            result.confidencePolicyVersion(),
            result.confidenceCalibration(),

            result.provider(),
            result.model(),
            result.fallbackUsed(),

            result.createdAt(),
            result.updatedAt(),
            result.completedAt(),
            result.classificationCreatedAt()
        );
    }
}
