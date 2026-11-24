package com.verifiedai.admin.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminBillingSupportView(
    String schemaVersion,
    UUID userId,
    String accountStatus,
    AdminUserLookupView.EntitlementSummaryView entitlement,
    List<SubscriptionView> subscriptions,
    List<TransactionView> transactions,
    List<AdminUserLookupView.QuotaSummaryView> quotaLedgers,
    List<BillingTransitionView> transitionAudit,
    boolean rawContentRedacted,
    UUID auditId,
    Instant generatedAt,
    int llmTokensUsed,
    boolean trainingEligible
) {
    public AdminBillingSupportView {
        subscriptions = subscriptions == null ? List.of() : List.copyOf(subscriptions);
        transactions = transactions == null ? List.of() : List.copyOf(transactions);
        quotaLedgers = quotaLedgers == null ? List.of() : List.copyOf(quotaLedgers);
        transitionAudit = transitionAudit == null ? List.of() : List.copyOf(transitionAudit);
    }

    public record SubscriptionView(
        UUID subscriptionId,
        String originalTransactionId,
        String currentTransactionId,
        String productId,
        String environment,
        String status,
        Boolean autoRenewStatus,
        Instant expiresAt,
        Instant gracePeriodExpiresAt,
        Instant revokedAt,
        Instant lastReconciledAt,
        Instant updatedAt
    ) {
    }

    public record TransactionView(
        UUID id,
        String transactionId,
        String originalTransactionId,
        String productId,
        String environment,
        Instant purchaseDate,
        Instant expiresDate,
        Instant revocationDate,
        String transactionReason,
        String ownershipType,
        boolean accessRevoked,
        String offerFamily,
        Instant createdAt
    ) {
    }

    public record BillingTransitionView(
        UUID id,
        String fromPackage,
        String toPackage,
        String fromStatus,
        String toStatus,
        String reason,
        Instant occurredAt,
        Instant recordedAt
    ) {
    }
}
