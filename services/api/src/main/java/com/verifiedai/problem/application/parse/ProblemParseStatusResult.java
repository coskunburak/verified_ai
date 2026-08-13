package com.verifiedai.problem.application.parse;

import java.time.Instant;
import java.util.UUID;

public record ProblemParseStatusResult(
    UUID parseJobId,
    UUID problemSessionId,
    UUID recognitionEvidenceId,
    Integer recognitionEvidenceRevision,
    String jobStatus,
    String capability,
    int attemptCount,
    int maxAttempts,
    String lastErrorCode,
    String lastFailureClass,
    UUID problemParseId,
    Integer parseRevision,
    String supportStatus,
    String unsupportedReason,
    boolean reviewRequired,
    String schemaVersion,
    String promptId,
    String promptVersion,
    String routePolicyVersion,
    String provider,
    String model,
    String normalizedProblemJson,
    Instant createdAt,
    Instant updatedAt,
    Instant completedAt
) {
    public static ProblemParseStatusResult notStarted(UUID problemSessionId) {
        return new ProblemParseStatusResult(
            null,
            problemSessionId,
            null,
            null,
            "NOT_STARTED",
            "PROBLEM_NORMALIZE",
            0,
            0,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
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
