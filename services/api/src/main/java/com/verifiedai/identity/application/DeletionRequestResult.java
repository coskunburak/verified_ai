package com.verifiedai.identity.application;

import java.time.Instant;
import java.util.UUID;

public record DeletionRequestResult(
    UUID userId,
    String status,
    Instant deletionRequestedAt,
    Instant deletedAt
) {}
