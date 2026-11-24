package com.verifiedai.admin.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminUserLookupView(
    String schemaVersion,
    String lookupType,
    UUID userId,
    String accountStatus,
    Instant accountCreatedAt,
    Instant accountUpdatedAt,
    Instant deletionRequestedAt,
    Instant deletedAt,
    List<SessionSummaryView> recentSessions,
    EntitlementSummaryView entitlement,
    QuotaSummaryView activeQuotaLedger,
    int wrongAnswerReportCount,
    boolean rawContentRedacted,
    UUID auditId,
    Instant generatedAt,
    int llmTokensUsed,
    boolean trainingEligible
) {
    public AdminUserLookupView {
        recentSessions = recentSessions == null ? List.of() : List.copyOf(recentSessions);
    }

    public record SessionSummaryView(
        UUID sessionId,
        String status,
        Instant createdAt,
        Instant lastSeenAt,
        Instant expiresAt,
        Instant revokedAt,
        String revocationReason
    ) {
    }

    public record EntitlementSummaryView(
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
    }

    public record QuotaSummaryView(
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
    }
}
