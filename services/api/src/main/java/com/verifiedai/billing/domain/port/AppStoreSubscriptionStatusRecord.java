package com.verifiedai.billing.domain.port;

public record AppStoreSubscriptionStatusRecord(
    String originalTransactionId,
    Integer appStoreStatus,
    String signedTransactionInfo,
    String signedRenewalInfo
) {}
