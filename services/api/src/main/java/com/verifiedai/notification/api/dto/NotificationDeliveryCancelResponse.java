package com.verifiedai.notification.api.dto;

import com.verifiedai.notification.application.CancelNotificationResult;
import java.time.Instant;
import java.util.UUID;

public record NotificationDeliveryCancelResponse(
    String schemaVersion,
    UUID deliveryId,
    String status,
    Instant cancelledAt,
    boolean trainingEligible
) {
    public static NotificationDeliveryCancelResponse from(CancelNotificationResult result) {
        return new NotificationDeliveryCancelResponse(
            "notification-delivery-cancel-v1",
            result.deliveryId(),
            result.status().name(),
            result.cancelledAt(),
            result.trainingEligible()
        );
    }
}
