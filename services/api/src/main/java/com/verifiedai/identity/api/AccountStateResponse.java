package com.verifiedai.identity.api;

import com.verifiedai.identity.application.AccountStateResult;
import java.time.Instant;
import java.util.UUID;

record AccountStateResponse(
    UUID userId,
    String status,
    Instant createdAt,
    Instant deletionRequestedAt,
    Instant deletedAt
) {
    static AccountStateResponse from(AccountStateResult result) {
        return new AccountStateResponse(
            result.userId(),
            result.status(),
            result.createdAt(),
            result.deletionRequestedAt(),
            result.deletedAt()
        );
    }
}
