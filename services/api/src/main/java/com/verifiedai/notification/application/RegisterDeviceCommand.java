package com.verifiedai.notification.application;

/**
 * Application command for registering or updating a push notification device token.
 */
public record RegisterDeviceCommand(
    String platform,
    String environment,
    String deviceToken,
    String osPermissionStatus,
    String appVersion,
    String locale,
    Boolean trainingEligible
) {
    @Override
    public String toString() {
        return "RegisterDeviceCommand[platform=" + platform
            + ", environment=" + environment
            + ", osPermissionStatus=" + osPermissionStatus
            + ", appVersion=" + appVersion
            + ", locale=" + locale + "]";
    }
}
