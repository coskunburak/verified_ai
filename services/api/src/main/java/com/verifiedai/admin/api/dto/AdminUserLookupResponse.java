package com.verifiedai.admin.api.dto;

import com.verifiedai.admin.application.AdminUserLookupView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminUserLookupResponse(
    String schemaVersion,
    String lookupType,
    UUID userId,
    String accountStatus,
    Instant accountCreatedAt,
    Instant accountUpdatedAt,
    Instant deletionRequestedAt,
    Instant deletedAt,
    List<SessionSummaryDto> recentSessions,
    EntitlementSummaryDto entitlement,
    QuotaSummaryDto activeQuotaLedger,
    int wrongAnswerReportCount,
    boolean rawContentRedacted,
    UUID auditId,
    Instant generatedAt,
    int llmTokensUsed,
    boolean trainingEligible
) {
    public static AdminUserLookupResponse from(AdminUserLookupView view) {
        return new AdminUserLookupResponse(
            view.schemaVersion(),
            view.lookupType(),
            view.userId(),
            view.accountStatus(),
            view.accountCreatedAt(),
            view.accountUpdatedAt(),
            view.deletionRequestedAt(),
            view.deletedAt(),
            view.recentSessions().stream()
                .map(s -> new SessionSummaryDto(
                    s.sessionId(),
                    s.status(),
                    s.createdAt(),
                    s.lastSeenAt(),
                    s.expiresAt(),
                    s.revokedAt(),
                    s.revocationReason()
                ))
                .toList(),
            view.entitlement() == null ? null : EntitlementSummaryDto.from(view.entitlement()),
            view.activeQuotaLedger() == null ? null : QuotaSummaryDto.from(view.activeQuotaLedger()),
            view.wrongAnswerReportCount(),
            view.rawContentRedacted(),
            view.auditId(),
            view.generatedAt(),
            view.llmTokensUsed(),
            view.trainingEligible()
        );
    }

    public record SessionSummaryDto(
        UUID sessionId,
        String status,
        Instant createdAt,
        Instant lastSeenAt,
        Instant expiresAt,
        Instant revokedAt,
        String revocationReason
    ) {
    }

    public record EntitlementSummaryDto(
        UUID entitlementId,
        String tier,
        String packageCode,
        String source,
        String status,
        Instant effectiveAt,
        Instant expiresAt,
        Instant gracePeriodExpiresAt,
        String originalTransactionId,
        String environment,
        Instant lastVerifiedAt,
        Instant updatedAt,
        long version
    ) {
        public static EntitlementSummaryDto from(AdminUserLookupView.EntitlementSummaryView e) {
            return new EntitlementSummaryDto(
                e.entitlementId(),
                e.tier(),
                e.packageCode(),
                e.source(),
                e.status(),
                e.effectiveAt(),
                e.expiresAt(),
                e.gracePeriodExpiresAt(),
                e.originalTransactionId(),
                e.environment(),
                e.lastVerifiedAt(),
                e.updatedAt(),
                e.version()
            );
        }
    }

    public record QuotaSummaryDto(
        String periodKey,
        String tierCode,
        int aiVerificationsUsed,
        int aiVerificationsLimit,
        int hybridTwinsUsed,
        int hybridTwinsLimit,
        long accumulatedAiCostMicros,
        double accumulatedAiCostUsd,
        long aiCostCapMicros,
        boolean costCircuitBreakerTripped,
        Instant resetsAt,
        Instant updatedAt
    ) {
        public static QuotaSummaryDto from(AdminUserLookupView.QuotaSummaryView q) {
            return new QuotaSummaryDto(
                q.periodKey(),
                q.tierCode(),
                q.aiVerificationsUsed(),
                q.aiVerificationsLimit(),
                q.hybridTwinsUsed(),
                q.hybridTwinsLimit(),
                q.accumulatedAiCostMicros(),
                q.accumulatedAiCostUsd(),
                q.aiCostCapMicros(),
                q.costCircuitBreakerTripped(),
                q.resetsAt(),
                q.updatedAt()
            );
        }
    }
}
