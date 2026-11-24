package com.verifiedai.admin.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AdminAiOperationsView(
    String schemaVersion,
    LocalDate fromDate,
    LocalDate toDate,
    long totalCalls,
    long totalFailures,
    long totalCostMicros,
    double totalCostUsd,
    long recentFlagAuditCount,
    List<ProviderHealthView> providerHealth,
    List<ActiveRouteView> activeRoutes,
    List<RedactedFailureSampleView> failureSamples,
    List<KillSwitchFlagView> killSwitchFlags,
    boolean rawContentRedacted,
    UUID auditId,
    Instant generatedAt,
    int llmTokensUsed,
    boolean trainingEligible
) {
    public AdminAiOperationsView {
        providerHealth = providerHealth == null ? List.of() : List.copyOf(providerHealth);
        activeRoutes = activeRoutes == null ? List.of() : List.copyOf(activeRoutes);
        failureSamples = failureSamples == null ? List.of() : List.copyOf(failureSamples);
        killSwitchFlags = killSwitchFlags == null ? List.of() : List.copyOf(killSwitchFlags);
    }

    public record ProviderHealthView(
        String provider,
        String model,
        long totalCalls,
        long successCalls,
        long failureCalls,
        long totalCostMicros,
        double totalCostUsd,
        int avgLatencyMs
    ) {
    }

    public record ActiveRouteView(
        String capability,
        String routePolicyVersion,
        String routeId,
        String provider,
        String model,
        String promptId,
        String promptVersion,
        String schemaVersion
    ) {
    }

    public record RedactedFailureSampleView(
        UUID usageRecordId,
        String capability,
        String provider,
        String model,
        String status,
        String failureClass,
        Boolean retryable,
        Boolean fallbackUsed,
        String correlationId,
        String traceId,
        Instant createdAt
    ) {
    }

    public record KillSwitchFlagView(
        String flagKey,
        String category,
        boolean enabled,
        boolean killSwitchActive,
        int rolloutBasisPoints,
        String currentValueJson,
        long version,
        Instant updatedAt
    ) {
    }
}
