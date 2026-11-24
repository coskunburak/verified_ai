package com.verifiedai.admin.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminAuditEntryView(
    UUID id,
    String action,
    String targetType,
    String targetId,
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
    public record AdminAuditListView(
        String schemaVersion,
        int entryCount,
        List<AdminAuditEntryView> entries,
        Instant generatedAt,
        int llmTokensUsed,
        boolean trainingEligible
    ) {
        public AdminAuditListView {
            entries = entries == null ? List.of() : List.copyOf(entries);
        }
    }
}
