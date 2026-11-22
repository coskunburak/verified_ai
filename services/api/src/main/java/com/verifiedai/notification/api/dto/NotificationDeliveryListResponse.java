package com.verifiedai.notification.api.dto;

import com.verifiedai.notification.application.NotificationDeliverySummaryView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NotificationDeliveryListResponse(
    String schemaVersion,
    List<Item> items,
    boolean trainingEligible
) {
    public static NotificationDeliveryListResponse from(List<NotificationDeliverySummaryView> views) {
        return new NotificationDeliveryListResponse(
            "notification-delivery-list-v1",
            views.stream().map(Item::from).toList(),
            false
        );
    }

    public record Item(
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
        public static Item from(NotificationDeliverySummaryView view) {
            return new Item(
                view.deliveryId(),
                view.category(),
                view.intentType(),
                view.channel(),
                view.locale(),
                view.templateKey(),
                view.deepLinkPath(),
                view.status(),
                view.suppressionReason(),
                view.scheduledFor(),
                view.effectiveSendAt(),
                view.expiresAt(),
                view.sentAt(),
                view.attemptCount(),
                view.createdAt()
            );
        }
    }
}
