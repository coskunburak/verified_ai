package com.verifiedai.admin.api.dto;

import com.verifiedai.admin.application.AdminBillingSupportView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminBillingSupportResponse(
    String schemaVersion,
    UUID userId,
    String accountStatus,
    AdminUserLookupResponse.EntitlementSummaryDto entitlement,
    List<SubscriptionDto> subscriptions,
    List<TransactionDto> transactions,
    List<AdminUserLookupResponse.QuotaSummaryDto> quotaLedgers,
    List<BillingTransitionDto> transitionAudit,
    boolean rawContentRedacted,
    UUID auditId,
    Instant generatedAt,
    int llmTokensUsed,
    boolean trainingEligible
) {
    public static AdminBillingSupportResponse from(AdminBillingSupportView view) {
        return new AdminBillingSupportResponse(
            view.schemaVersion(),
            view.userId(),
            view.accountStatus(),
            view.entitlement() == null ? null : AdminUserLookupResponse.EntitlementSummaryDto.from(view.entitlement()),
            view.subscriptions().stream()
                .map(s -> new SubscriptionDto(
                    s.subscriptionId(),
                    s.originalTransactionId(),
                    s.currentTransactionId(),
                    s.productId(),
                    s.environment(),
                    s.status(),
                    s.autoRenewStatus(),
                    s.expiresAt(),
                    s.gracePeriodExpiresAt(),
                    s.revokedAt(),
                    s.lastReconciledAt(),
                    s.updatedAt()
                ))
                .toList(),
            view.transactions().stream()
                .map(t -> new TransactionDto(
                    t.id(),
                    t.transactionId(),
                    t.originalTransactionId(),
                    t.productId(),
                    t.environment(),
                    t.purchaseDate(),
                    t.expiresDate(),
                    t.revocationDate(),
                    t.transactionReason(),
                    t.ownershipType(),
                    t.accessRevoked(),
                    t.offerFamily(),
                    t.createdAt()
                ))
                .toList(),
            view.quotaLedgers().stream()
                .map(AdminUserLookupResponse.QuotaSummaryDto::from)
                .toList(),
            view.transitionAudit().stream()
                .map(b -> new BillingTransitionDto(
                    b.id(),
                    b.fromPackage(),
                    b.toPackage(),
                    b.fromStatus(),
                    b.toStatus(),
                    b.reason(),
                    b.occurredAt(),
                    b.recordedAt()
                ))
                .toList(),
            view.rawContentRedacted(),
            view.auditId(),
            view.generatedAt(),
            view.llmTokensUsed(),
            view.trainingEligible()
        );
    }

    public record SubscriptionDto(
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

    public record TransactionDto(
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

    public record BillingTransitionDto(
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
