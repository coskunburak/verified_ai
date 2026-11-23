package com.verifiedai.notification.application;

import java.util.UUID;

/**
 * Audit command recording a deterministic skip outcome (e.g., already completed or not eligible).
 */
public record LifecycleSkipCommand(
    UUID userId,
    String sourceModule,
    String category,
    String intentType,
    String idempotencyKey,
    String templateKey,
    String decision,
    String reasonCode,
    String correlationId
) {
}
