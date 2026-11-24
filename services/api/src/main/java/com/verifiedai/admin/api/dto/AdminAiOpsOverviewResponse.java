package com.verifiedai.admin.api.dto;

import com.verifiedai.admin.application.AdminAiOperationsView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AdminAiOpsOverviewResponse(
    String schemaVersion,
    LocalDate fromDate,
    LocalDate toDate,
    long totalCalls,
    long totalFailures,
    long totalCostMicros,
    double totalCostUsd,
    long recentFlagAuditCount,
    List<ProviderHealthDto> providerHealth,
    List<ActiveRouteDto> activeRoutes,
    List<RedactedFailureSampleDto> failureSamples,
    List<KillSwitchFlagDto> killSwitchFlags,
    boolean rawContentRedacted,
    UUID auditId,
    Instant generatedAt,
    int llmTokensUsed,
    boolean trainingEligible
) {
    public static AdminAiOpsOverviewResponse from(AdminAiOperationsView view) {
        return new AdminAiOpsOverviewResponse(
            view.schemaVersion(),
            view.fromDate(),
            view.toDate(),
            view.totalCalls(),
            view.totalFailures(),
            view.totalCostMicros(),
            view.totalCostUsd(),
            view.recentFlagAuditCount(),
            view.providerHealth().stream()
                .map(p -> new ProviderHealthDto(
                    p.provider(),
                    p.model(),
                    p.totalCalls(),
                    p.successCalls(),
                    p.failureCalls(),
                    p.totalCostMicros(),
                    p.totalCostUsd(),
                    p.avgLatencyMs()
                ))
                .toList(),
            view.activeRoutes().stream()
                .map(r -> new ActiveRouteDto(
                    r.capability(),
                    r.routePolicyVersion(),
                    r.routeId(),
                    r.provider(),
                    r.model(),
                    r.promptId(),
                    r.promptVersion(),
                    r.schemaVersion()
                ))
                .toList(),
            view.failureSamples().stream()
                .map(f -> new RedactedFailureSampleDto(
                    f.usageRecordId(),
                    f.capability(),
                    f.provider(),
                    f.model(),
                    f.status(),
                    f.failureClass(),
                    f.retryable(),
                    f.fallbackUsed(),
                    f.correlationId(),
                    f.traceId(),
                    f.createdAt()
                ))
                .toList(),
            view.killSwitchFlags().stream()
                .map(k -> new KillSwitchFlagDto(
                    k.flagKey(),
                    k.category(),
                    k.enabled(),
                    k.killSwitchActive(),
                    k.rolloutBasisPoints(),
                    k.currentValueJson(),
                    k.version(),
                    k.updatedAt()
                ))
                .toList(),
            view.rawContentRedacted(),
            view.auditId(),
            view.generatedAt(),
            view.llmTokensUsed(),
            view.trainingEligible()
        );
    }

    public record ProviderHealthDto(
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

    public record ActiveRouteDto(
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

    public record RedactedFailureSampleDto(
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

    public record KillSwitchFlagDto(
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
