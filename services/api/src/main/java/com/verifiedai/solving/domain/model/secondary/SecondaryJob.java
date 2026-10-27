package com.verifiedai.solving.domain.model.secondary;

import java.time.Instant;
import java.util.UUID;

public record SecondaryJob(UUID id, UUID primaryJobId, UUID userId, UUID sessionId, UUID canonicalId,
    UUID parseId, int parseRevision, int canonicalRevision, UUID operationId, String policyVersion,
    String decision, String reason, SecondaryState state, String failureCode, int attempt,
    String routeJson, UUID ledgerRecordId, Instant createdAt, Instant deadline) { }
