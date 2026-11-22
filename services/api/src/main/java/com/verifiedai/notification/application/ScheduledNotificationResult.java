package com.verifiedai.notification.application;

import com.verifiedai.notification.domain.NotificationCategory;
import com.verifiedai.notification.domain.NotificationDeliveryStatus;
import com.verifiedai.notification.domain.NotificationIntentType;
import com.verifiedai.notification.domain.NotificationSuppressionReason;
import java.time.Instant;
import java.util.UUID;

/**
 * Result of scheduling a notification intent through {@link NotificationSchedulingPort}.
 */
public record ScheduledNotificationResult(
    UUID deliveryId,
    UUID userId,
    String idempotencyKey,
    NotificationCategory category,
    NotificationIntentType intentType,
    NotificationDeliveryStatus status,
    NotificationSuppressionReason suppressionReason,
    Instant scheduledFor,
    Instant effectiveSendAt,
    Instant expiresAt,
    boolean replayed,
    boolean trainingEligible
) {
    public String statusCode() {
        return status.name();
    }

    public String suppressionReasonCode() {
        return suppressionReason == null ? null : suppressionReason.name();
    }
}
