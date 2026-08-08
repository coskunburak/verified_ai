package com.verifiedai.billing.domain.model;

public record AppStoreProductMapping(
    String internalPlanId,
    String appStoreProductId,
    EntitlementTier entitlementTier,
    String subscriptionGroupId,
    String billingPeriod
) {
}
