package com.verifiedai.billing.application;

import com.verifiedai.billing.domain.model.AppStoreSubscriptionStatus;

public record ApplePurchaseEvidenceResult(
    String transactionId,
    String originalTransactionId,
    AppStoreSubscriptionStatus subscriptionStatus,
    EntitlementResult entitlement,
    boolean duplicate
) {
}
