package com.verifiedai.solving.domain.model;

import java.time.Instant;
import java.util.UUID;

public record SolveJob(UUID id, UUID userId, UUID sessionId, UUID canonicalId, UUID parseId,
    int parseRevision, String idempotencyKey, int attempt, UUID retryOf, UUID operationId,
    SolveState state, String failureCode, UUID ledgerRecordId, Instant createdAt, Instant deadline) { }
