package com.verifiedai.billing.domain.model;

import java.time.Instant;
import java.util.UUID;

public record VerifiedAppStoreTransaction(
    String transactionId,
    String originalTransactionId,
    String productId,
    String subscriptionGroupId,
    UUID appAccountToken,
    AppStoreEnvironment environment,
    Instant purchaseDate,
    Instant originalPurchaseDate,
    Instant expiresDate,
    Instant revocationDate,
    Instant signedDate,
    String transactionReason,
    String ownershipType
) {
    public boolean revoked() {
        return revocationDate != null;
    }

    public boolean expired(Instant now) {
        return expiresDate != null && !expiresDate.isAfter(now);
    }
}
