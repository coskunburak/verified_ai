package com.verifiedai.notification.api.dto;

import com.verifiedai.notification.application.RegisterDeviceCommand;

public record NotificationDeviceRegisterRequest(
    String platform,
    String environment,
    String deviceToken,
    String osPermissionStatus,
    String appVersion,
    String locale,
    Boolean trainingEligible
) {
    public RegisterDeviceCommand toCommand() {
        return new RegisterDeviceCommand(
            platform,
            environment,
            deviceToken,
            osPermissionStatus,
            appVersion,
            locale,
            trainingEligible
        );
    }

    @Override
    public String toString() {
        return "NotificationDeviceRegisterRequest[platform=" + platform
            + ", environment=" + environment
            + ", osPermissionStatus=" + osPermissionStatus
            + ", appVersion=" + appVersion
            + ", locale=" + locale + "]";
    }
}
