package com.verifiedai.notification.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Privacy-minimized summary of a user's notification delivery record.
 */
public record NotificationDeliverySummaryView(
    UUID deliveryId,
    String category,
    String intentType,
    String channel,
    String locale,
    String templateKey,
    String deepLinkPath,
    String status,
    String suppressionReason,
    Instant scheduledFor,
    Instant effectiveSendAt,
    Instant expiresAt,
    Instant sentAt,
    int attemptCount,
    Instant createdAt
) {
}
