package com.verifiedai.notification.domain;

/**
 * Deterministic reasons for suppressing a notification delivery without dispatch.
 */
public enum NotificationSuppressionReason {
    USER_OPTED_OUT,
    PUSH_GLOBALLY_DISABLED,
    DAILY_CAP_REACHED,
    NO_ACTIVE_DEVICE,
    FEATURE_DISABLED,
    ACCOUNT_NOT_ACTIVE,
    INTENT_EXPIRED
}
