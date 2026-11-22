package com.verifiedai.notification.application;

import com.verifiedai.notification.domain.NotificationCategory;
import com.verifiedai.notification.domain.NotificationChannel;
import com.verifiedai.notification.domain.NotificationIntentType;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable command accepted by {@link NotificationSchedulingPort#schedule(ScheduleNotificationCommand)}.
 */
public record ScheduleNotificationCommand(
    UUID userId,
    String idempotencyKey,
    NotificationCategory category,
    NotificationIntentType intentType,
    NotificationChannel channel,
    String locale,
    String templateKey,
    Map<String, String> templateParams,
    String deepLinkPath,
    Instant scheduledFor,
    Instant expiresAt,
    String sourceModule,
    String correlationId
) {
    public ScheduleNotificationCommand {
        templateParams = templateParams == null ? Map.of() : Map.copyOf(templateParams);
    }

    /**
     * String-typed factory allowing downstream Sprint 10.5 modules to schedule typed intents
     * without importing {@code com.verifiedai.notification.domain} directly.
     */
    public static ScheduleNotificationCommand of(
        UUID userId,
        String idempotencyKey,
        String category,
        String intentType,
        String channel,
        String locale,
        String templateKey,
        Map<String, String> templateParams,
        String deepLinkPath,
        Instant scheduledFor,
        Instant expiresAt,
        String sourceModule,
        String correlationId
    ) {
        return new ScheduleNotificationCommand(
            userId,
            idempotencyKey,
            NotificationCategory.valueOf(Objects.requireNonNull(category, "category")),
            NotificationIntentType.valueOf(Objects.requireNonNull(intentType, "intentType")),
            NotificationChannel.valueOf(Objects.requireNonNull(channel, "channel")),
            locale,
            templateKey,
            templateParams,
            deepLinkPath,
            scheduledFor,
            expiresAt,
            sourceModule,
            correlationId
        );
    }
}
