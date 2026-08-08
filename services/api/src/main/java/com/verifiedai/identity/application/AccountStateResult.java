package com.verifiedai.identity.application;

import java.time.Instant;
import java.util.UUID;

public record AccountStateResult(
    UUID userId,
    String status,
    Instant createdAt,
    Instant deletionRequestedAt,
    Instant deletedAt
) {}
