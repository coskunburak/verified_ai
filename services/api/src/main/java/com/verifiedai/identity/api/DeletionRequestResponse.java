package com.verifiedai.identity.api;

import com.verifiedai.identity.application.DeletionRequestResult;
import java.time.Instant;
import java.util.UUID;

record DeletionRequestResponse(
    UUID userId,
    String status,
    Instant deletionRequestedAt,
    Instant deletedAt
) {
    static DeletionRequestResponse from(DeletionRequestResult result) {
        return new DeletionRequestResponse(
            result.userId(),
            result.status(),
            result.deletionRequestedAt(),
            result.deletedAt()
        );
    }
}
