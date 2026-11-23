package com.verifiedai.featureflags.application;

import java.time.Instant;
import java.util.UUID;

public record FeatureFlagAuditEntryView(
    UUID auditId,
    String targetType,
    String targetKey,
    String action,
    UUID actorUserId,
    String actorRole,
    String idempotencyKey,
    String reason,
    String beforeStateJson,
    String afterStateJson,
    String correlationId,
    boolean trainingEligible,
    Instant occurredAt
) {
}
