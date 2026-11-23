package com.verifiedai.notification.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Idempotent cancellation result returned when obsolete pending lifecycle deliveries are cancelled.
 */
public record LifecycleCancellationResult(
    UUID userId,
    String category,
    String intentType,
    String idempotencyKey,
    UUID deliveryId,
    boolean cancelled,
    String reasonCode,
    Instant processedAt,
    boolean trainingEligible
) {
}
