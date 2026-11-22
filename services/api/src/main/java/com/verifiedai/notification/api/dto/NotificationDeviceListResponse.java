package com.verifiedai.notification.api.dto;

import java.util.List;

public record NotificationDeviceListResponse(
    String schemaVersion,
    List<NotificationDeviceResponse> items,
    boolean trainingEligible
) {
    public static NotificationDeviceListResponse of(List<NotificationDeviceResponse> items) {
        return new NotificationDeviceListResponse("notification-device-list-v1", List.copyOf(items), false);
    }
}
