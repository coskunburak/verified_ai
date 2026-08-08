package com.verifiedai.billing.application;

import com.verifiedai.billing.domain.model.AppStoreSubscriptionStatus;

public record AppleNotificationIngestionResult(
    String notificationUuid,
    String processingStatus,
    AppStoreSubscriptionStatus subscriptionStatus,
    EntitlementResult entitlement
) {
}
