package com.verifiedai.featureflags.api.dto;

import com.verifiedai.featureflags.application.FeatureFlagAuditEntryView;
import com.verifiedai.featureflags.application.FeatureFlagGovernanceService.FeatureFlagAuditListView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FeatureFlagAuditListResponse(
    String schemaVersion,
    int entryCount,
    List<AuditEntryItem> entries,
    Instant generatedAt,
    int llmTokensUsed,
    boolean trainingEligible
) {
    public static FeatureFlagAuditListResponse from(FeatureFlagAuditListView view) {
        return new FeatureFlagAuditListResponse(
            view.schemaVersion(),
            view.entryCount(),
            view.entries().stream().map(AuditEntryItem::from).toList(),
            view.generatedAt(),
            view.llmTokensUsed(),
            view.trainingEligible()
        );
    }

    public record AuditEntryItem(
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
        public static AuditEntryItem from(FeatureFlagAuditEntryView view) {
            return new AuditEntryItem(
                view.auditId(),
                view.targetType(),
                view.targetKey(),
                view.action(),
                view.actorUserId(),
                view.actorRole(),
                view.idempotencyKey(),
                view.reason(),
                view.beforeStateJson(),
                view.afterStateJson(),
                view.correlationId(),
                view.trainingEligible(),
                view.occurredAt()
            );
        }
    }
}
