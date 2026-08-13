package com.verifiedai.problem.application.recognition;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RecognitionStatusResult(
    UUID recognitionJobId,
    UUID problemSessionId,
    UUID sourceAssetId,
    UUID inputDerivativeId,
    String status,
    String capability,
    int attemptCount,
    int maxAttempts,
    String lastErrorCode,
    String lastFailureClass,
    boolean reviewRequired,
    String schemaVersion,
    String promptId,
    String promptVersion,
    String routePolicyVersion,
    String provider,
    String model,
    int blockCount,
    List<RecognitionBlockResult> blocks,
    Instant createdAt,
    Instant updatedAt,
    Instant completedAt
) {
    public static RecognitionStatusResult notStarted(UUID problemSessionId) {
        return new RecognitionStatusResult(
            null,
            problemSessionId,
            null,
            null,
            "NOT_STARTED",
            "VISION_PARSE",
            0,
            0,
            null,
            null,
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            0,
            List.of(),
            null,
            null,
            null
        );
    }
}
