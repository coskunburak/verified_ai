package com.verifiedai.exam.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ExamScratchpadAttachedEvent(
        UUID eventId,
        UUID sessionId,
        UUID userId,
        int itemPosition,
        UUID assetId,
        int pageNumber,
        String captureSource,
        String bookletPublisher,
        String sha256Checksum,
        boolean trainingEligible,
        Instant occurredAt
) {
    public ExamScratchpadAttachedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(assetId, "assetId must not be null");
        Objects.requireNonNull(captureSource, "captureSource must not be null");
        Objects.requireNonNull(sha256Checksum, "sha256Checksum must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (trainingEligible) {
            throw new IllegalArgumentException("trainingEligible must be false per ADR-006");
        }
    }
}
