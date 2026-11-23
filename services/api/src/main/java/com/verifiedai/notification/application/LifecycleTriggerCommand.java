package com.verifiedai.notification.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * String-typed trigger command accepted by {@link LifecycleMessagingPort#evaluateAndSchedule(LifecycleTriggerCommand)}
 * so downstream modules remain isolated from {@code com.verifiedai.notification.domain}.
 */
public record LifecycleTriggerCommand(
    UUID userId,
    String sourceModule,
    String category,
    String intentType,
    String idempotencyKey,
    String locale,
    String templateKey,
    Map<String, String> templateParams,
    String deepLinkPath,
    Instant scheduledFor,
    Instant expiresAt,
    String correlationId
) {
    public LifecycleTriggerCommand {
        templateParams = templateParams == null ? Map.of() : Map.copyOf(templateParams);
    }
}
