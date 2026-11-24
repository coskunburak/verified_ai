package com.verifiedai.admin.api.dto;

import com.verifiedai.admin.application.AdminAuditEntryView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminSupportAuditListResponse(
    String schemaVersion,
    int entryCount,
    List<AuditEntryDto> entries,
    Instant generatedAt,
    int llmTokensUsed,
    boolean trainingEligible
) {
    public static AdminSupportAuditListResponse from(AdminAuditEntryView.AdminAuditListView view) {
        return new AdminSupportAuditListResponse(
            view.schemaVersion(),
            view.entryCount(),
            view.entries().stream()
                .map(e -> new AuditEntryDto(
                    e.id(),
                    e.action(),
                    e.targetType(),
                    e.targetId(),
                    e.actorUserId(),
                    e.actorRole(),
                    e.idempotencyKey(),
                    e.reason(),
                    e.beforeStateJson(),
                    e.afterStateJson(),
                    e.correlationId(),
                    e.trainingEligible(),
                    e.occurredAt()
                ))
                .toList(),
            view.generatedAt(),
            view.llmTokensUsed(),
            view.trainingEligible()
        );
    }

    public record AuditEntryDto(
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
    }
}
