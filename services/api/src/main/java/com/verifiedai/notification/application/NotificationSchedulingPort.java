package com.verifiedai.notification.application;

import java.util.UUID;

/**
 * Public Spring Modulith SPI port consumed by downstream lifecycle/billing/studyplan modules in Sprint 10.5.
 */
public interface NotificationSchedulingPort {

    ScheduledNotificationResult schedule(ScheduleNotificationCommand command);

    CancelNotificationResult cancel(UUID userId, UUID deliveryId, String reasonCode, String correlationId);

    NotificationPreferenceView getPreferences(UUID userId);
}
