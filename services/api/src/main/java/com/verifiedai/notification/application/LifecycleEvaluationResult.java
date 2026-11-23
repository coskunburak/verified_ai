package com.verifiedai.notification.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Public outcome returned by {@link LifecycleMessagingPort} trigger evaluations and skip recordings.
 */
public record LifecycleEvaluationResult(
    UUID evaluationId,
    UUID userId,
    String category,
    String intentType,
    String idempotencyKey,
    String decision,
    String reasonCode,
    UUID deliveryId,
    Instant cooldownUntil,
    Instant evaluatedAt,
    boolean trainingEligible
) {
}
