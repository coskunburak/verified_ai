package com.verifiedai.problem.application.classification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProblemClassificationStatusResult(
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
    Long providerLatencyMs,
    Long estimatedCostMicros,

    Instant createdAt,
    Instant updatedAt,
    Instant completedAt,
    Instant classificationCreatedAt
) {

    public ProblemClassificationStatusResult {
        secondarySkillIds =
            secondarySkillIds == null
                ? List.of()
                : List.copyOf(secondarySkillIds);
    }

    public static ProblemClassificationStatusResult notStarted(
        UUID problemSessionId,
        UUID canonicalProblemId,
        int canonicalProblemRevision,
        String ontologyVersion
    ) {
        return new ProblemClassificationStatusResult(
            null,
            problemSessionId,
            canonicalProblemId,
            canonicalProblemRevision,

            "NOT_STARTED",
            "PROBLEM_CLASSIFY",
            0,
            0,
            null,
            null,

            null,
            null,
            null,
            null,
            null,

            ontologyVersion,
            ProblemClassificationContract.PROJECTION_VERSION,
            ProblemClassificationContract.SCHEMA_VERSION,

            null,
            null,
            null,
            List.of(),

            null,
            ProblemClassificationContract.DIFFICULTY_POLICY_VERSION,

            null,
            ProblemClassificationContract.CONFIDENCE_POLICY_VERSION,
            null,

            null,
            null,
            null,
            null,
            null,

            null,
            null,
            null,
            null
        );
    }
}
