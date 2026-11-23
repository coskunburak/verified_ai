package com.verifiedai.notification.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Privacy-safe projection of a user's active lifecycle cooldowns and recent deterministic evaluation decisions.
 */
public record LifecycleSummaryView(
    String schemaVersion,
    UUID userId,
    boolean lifecycleCampaignsEnabled,
    List<CooldownItem> activeCooldowns,
    List<EvaluationItem> recentEvaluations,
    int llmTokensUsed,
    boolean trainingEligible,
    Instant generatedAt
) {
    public LifecycleSummaryView {
        activeCooldowns = activeCooldowns == null ? List.of() : List.copyOf(activeCooldowns);
        recentEvaluations = recentEvaluations == null ? List.of() : List.copyOf(recentEvaluations);
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
    }
}
