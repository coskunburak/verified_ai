package com.verifiedai.identity.application;

import java.time.Instant;
import java.util.UUID;

public record DataExportResult(
    UUID exportId,
    String status,
    String schemaVersion,
    Instant requestedAt,
    Instant completedAt,
    Instant downloadedAt,
    Instant expiresAt
) {}
