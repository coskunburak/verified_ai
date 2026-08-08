package com.verifiedai.billing.application;

import com.verifiedai.billing.domain.model.EntitlementTier;

public record AppleBillingProductResult(
    String internalPlanId,
    String appStoreProductId,
    EntitlementTier entitlementTier,
    String subscriptionGroupId,
    String billingPeriod
) {
}
