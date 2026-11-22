package com.verifiedai.notification.application;

import com.verifiedai.notification.domain.NotificationDeliveryStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Result of cancelling a pending notification delivery.
 */
public record CancelNotificationResult(
    UUID deliveryId,
    NotificationDeliveryStatus status,
    Instant cancelledAt,
    boolean trainingEligible
) {
}
