package com.verifiedai.billing.domain.model;

public enum AppStoreNotificationProcessingStatus {
    RECEIVED,
    PROCESSING,
    PROCESSED,
    FAILED_RETRYABLE,
    FAILED_TERMINAL
}
