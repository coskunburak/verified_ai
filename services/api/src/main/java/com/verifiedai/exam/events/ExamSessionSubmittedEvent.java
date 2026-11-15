package com.verifiedai.exam.events;

import com.verifiedai.exam.domain.ExamSessionStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ExamSessionSubmittedEvent(
        UUID eventId,
        UUID sessionId,
        UUID userId,
        UUID assemblyId,
        UUID blueprintId,
        String examCode,
        ExamSessionStatus finalStatus,
        String submissionReason,
        int answeredItemCount,
        int flaggedItemCount,
        int omittedItemCount,
        int scratchpadAttachmentCount,
        int f28QueuedAssetCount,
        boolean trainingEligible,
        Instant occurredAt
) {
    public ExamSessionSubmittedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(assemblyId, "assemblyId must not be null");
        Objects.requireNonNull(blueprintId, "blueprintId must not be null");
        Objects.requireNonNull(examCode, "examCode must not be null");
        Objects.requireNonNull(finalStatus, "finalStatus must not be null");
        Objects.requireNonNull(submissionReason, "submissionReason must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (trainingEligible) {
            throw new IllegalArgumentException("trainingEligible must be false per ADR-006");
        }
    }
}
