package com.verifiedai.billing.domain.model;

import java.time.Instant;

public record VerifiedAppStoreRenewalInfo(
    String originalTransactionId,
    String productId,
    String autoRenewProductId,
    Boolean autoRenewStatus,
    Boolean inBillingRetryPeriod,
    Instant gracePeriodExpiresDate,
    Instant renewalDate,
    Instant signedDate
) {
}
