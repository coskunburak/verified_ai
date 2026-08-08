package com.verifiedai.billing.domain.model;

import java.time.Instant;

public record VerifiedAppStoreNotification(
    String notificationUuid,
    String notificationType,
    String subtype,
    AppStoreEnvironment environment,
    Integer appStoreStatus,
    Instant signedDate,
    VerifiedAppStoreTransaction transaction,
    VerifiedAppStoreRenewalInfo renewalInfo
) {
}
