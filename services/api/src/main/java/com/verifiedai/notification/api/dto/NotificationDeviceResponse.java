package com.verifiedai.notification.api.dto;

import com.verifiedai.notification.application.RegisteredDeviceView;
import java.time.Instant;
import java.util.UUID;

public record NotificationDeviceResponse(
    String schemaVersion,
    UUID deviceId,
    String platform,
    String environment,
    String tokenLast4,
    String osPermissionStatus,
    String appVersion,
    String locale,
    boolean active,
    long version,
    boolean trainingEligible,
    Instant lastSeenAt,
    Instant revokedAt,
    Instant updatedAt
) {
    public static NotificationDeviceResponse from(RegisteredDeviceView view) {
        return new NotificationDeviceResponse(
            view.schemaVersion(),
            view.deviceId(),
            view.platform(),
            view.environment(),
            view.tokenLast4(),
            view.osPermissionStatus(),
            view.appVersion(),
            view.locale(),
            view.active(),
            view.version(),
            view.trainingEligible(),
            view.lastSeenAt(),
            view.revokedAt(),
            view.updatedAt()
        );
    }
}
