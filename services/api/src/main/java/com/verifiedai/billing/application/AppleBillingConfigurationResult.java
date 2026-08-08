package com.verifiedai.billing.application;

import com.verifiedai.billing.domain.model.AppStoreEnvironment;
import java.util.List;
import java.util.UUID;

public record AppleBillingConfigurationResult(
    UUID appAccountToken,
    boolean purchaseAvailable,
    AppStoreEnvironment environment,
    List<AppleBillingProductResult> products
) {
}
