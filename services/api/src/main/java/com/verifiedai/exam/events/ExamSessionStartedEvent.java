package com.verifiedai.exam.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ExamSessionStartedEvent(
        UUID eventId,
        UUID sessionId,
        UUID userId,
        UUID assemblyId,
        UUID blueprintId,
        String examCode,
        int effectiveDurationSeconds,
        Instant serverDeadlineAt,
        boolean trainingEligible,
        Instant occurredAt
) {
    public ExamSessionStartedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(assemblyId, "assemblyId must not be null");
        Objects.requireNonNull(blueprintId, "blueprintId must not be null");
        Objects.requireNonNull(examCode, "examCode must not be null");
        Objects.requireNonNull(serverDeadlineAt, "serverDeadlineAt must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (trainingEligible) {
            throw new IllegalArgumentException("trainingEligible must be false per ADR-006");
        }
    }
}
