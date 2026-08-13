package com.verifiedai.ai.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AiExecutionContext(
    UUID operationId,
    UUID userId,
    UUID problemSessionId,
    String correlationId,
    String traceId,
    String idempotencyKey,
    Instant deadline,
    Map<String, String> metadata
) {

    public AiExecutionContext {
        if (operationId == null) {
            throw new IllegalArgumentException(
                "operationId is required"
            );
        }

        correlationId =
            correlationId == null
                || correlationId.isBlank()
                ? "unavailable"
                : correlationId;

        idempotencyKey =
            idempotencyKey == null
                || idempotencyKey.isBlank()
                ? operationId.toString()
                : idempotencyKey;

        metadata =
            metadata == null
                ? Map.of()
                : Map.copyOf(metadata);
    }

    public static AiExecutionContext forJobAttempt(
        UUID jobId,
        int attemptNumber,
        UUID userId,
        UUID problemSessionId,
        String correlationId
    ) {
        if (jobId == null) {
            throw new IllegalArgumentException(
                "jobId is required"
            );
        }

        if (attemptNumber < 1) {
            throw new IllegalArgumentException(
                "attemptNumber must be >= 1"
            );
        }

        UUID operationId =
            UUID.nameUUIDFromBytes(
                (
                    jobId
                        + ":"
                        + attemptNumber
                ).getBytes(StandardCharsets.UTF_8)
            );

        return new AiExecutionContext(
            operationId,
            userId,
            problemSessionId,
            correlationId,
            null,
            jobId.toString(),
            null,
            Map.of()
        );
    }

    public static AiExecutionContext compatibility(
        UUID problemSessionId
    ) {
        return new AiExecutionContext(
            UUID.randomUUID(),
            null,
            problemSessionId,
            "unavailable",
            null,
            null,
            null,
            Map.of()
        );
    }
}
