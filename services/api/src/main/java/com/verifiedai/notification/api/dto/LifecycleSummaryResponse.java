package com.verifiedai.notification.api.dto;

import com.verifiedai.notification.application.LifecycleSummaryView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LifecycleSummaryResponse(
    String schemaVersion,
    UUID userId,
    boolean lifecycleCampaignsEnabled,
    List<CooldownItem> activeCooldowns,
    List<EvaluationItem> recentEvaluations,
    int llmTokensUsed,
    boolean trainingEligible,
    Instant generatedAt
) {
    public static LifecycleSummaryResponse from(LifecycleSummaryView view) {
        return new LifecycleSummaryResponse(
            view.schemaVersion(),
            view.userId(),
            view.lifecycleCampaignsEnabled(),
            view.activeCooldowns().stream().map(CooldownItem::from).toList(),
            view.recentEvaluations().stream().map(EvaluationItem::from).toList(),
            view.llmTokensUsed(),
            view.trainingEligible(),
            view.generatedAt()
        );
    }

    public record CooldownItem(
        String category,
        String intentType,
        String lastIdempotencyKey,
        UUID lastDeliveryId,
        Instant lastTriggeredAt,
        Instant cooldownUntil,
        boolean active
    ) {
        public static CooldownItem from(LifecycleSummaryView.CooldownItem item) {
            return new CooldownItem(
                item.category(),
                item.intentType(),
                item.lastIdempotencyKey(),
                item.lastDeliveryId(),
                item.lastTriggeredAt(),
                item.cooldownUntil(),
                item.active()
            );
        }
    }

    public record EvaluationItem(
        UUID evaluationId,
        String sourceModule,
        String category,
        String intentType,
        String idempotencyKey,
        String templateKey,
        String decision,
        String reasonCode,
        UUID deliveryId,
        Instant evaluatedAt
    ) {
        public static EvaluationItem from(LifecycleSummaryView.EvaluationItem item) {
            return new EvaluationItem(
                item.evaluationId(),
                item.sourceModule(),
                item.category(),
                item.intentType(),
                item.idempotencyKey(),
                item.templateKey(),
                item.decision(),
                item.reasonCode(),
                item.deliveryId(),
                item.evaluatedAt()
            );
        }
    }
}
