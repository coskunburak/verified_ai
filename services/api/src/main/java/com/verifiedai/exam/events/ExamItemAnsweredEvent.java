package com.verifiedai.exam.events;

import com.verifiedai.exam.domain.ExamItemResponseState;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ExamItemAnsweredEvent(
        UUID eventId,
        UUID sessionId,
        UUID userId,
        int itemPosition,
        String questionCode,
        ExamItemResponseState responseState,
        int autosaveVersion,
        int timeSpentSeconds,
        boolean trainingEligible,
        Instant occurredAt
) {
    public ExamItemAnsweredEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(questionCode, "questionCode must not be null");
        Objects.requireNonNull(responseState, "responseState must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (trainingEligible) {
            throw new IllegalArgumentException("trainingEligible must be false per ADR-006");
        }
    }
}
