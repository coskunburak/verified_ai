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
    String ownershipType,
    boolean freeTrial
) {
    public VerifiedAppStoreTransaction(String transactionId, String originalTransactionId, String productId,
        String subscriptionGroupId, UUID appAccountToken, AppStoreEnvironment environment, Instant purchaseDate,
        Instant originalPurchaseDate, Instant expiresDate, Instant revocationDate, Instant signedDate,
        String transactionReason, String ownershipType) {
        this(transactionId, originalTransactionId, productId, subscriptionGroupId, appAccountToken, environment,
            purchaseDate, originalPurchaseDate, expiresDate, revocationDate, signedDate, transactionReason, ownershipType, false);
    }

    public boolean revoked() {
        return revocationDate != null;
    }

    public boolean expired(Instant now) {
        return expiresDate != null && !expiresDate.isAfter(now);
    }
}
