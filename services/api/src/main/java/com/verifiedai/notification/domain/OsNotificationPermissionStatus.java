package com.verifiedai.notification.domain;

/**
 * Client-reported operating system notification permission status.
 */
public enum OsNotificationPermissionStatus {
    NOT_DETERMINED(false),
    DENIED(false),
    AUTHORIZED(true),
    PROVISIONAL(true),
    EPHEMERAL(true);

    private final boolean deliverable;

    OsNotificationPermissionStatus(boolean deliverable) {
        this.deliverable = deliverable;
    }

    public boolean isDeliverable() {
        return deliverable;
    }
}
