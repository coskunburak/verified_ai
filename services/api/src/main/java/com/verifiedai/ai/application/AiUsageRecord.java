package com.verifiedai.ai.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AiUsageRecord(
    UUID id,
    UUID operationId,
    UUID userId,
    UUID problemSessionId,
    AiCapability capability,
    String routePolicyVersion,
    String routeId,
    String provider,
    String model,
    String promptId,
    String promptVersion,
    String schemaVersion,
    String providerRequestId,
    String providerResponseId,
    Status status,
    AiProviderFailureClass failureClass,
    boolean retryable,
    int attemptCount,
    boolean fallbackUsed,
    List<String> fallbackChain,
    Integer inputTokens,
    Integer outputTokens,
    Integer imageUnits,
    int requestUnits,
    long estimatedCostMicros,
    String currency,
    String pricingVersion,
    long providerLatencyMs,
    long gatewayLatencyMs,
    String correlationId,
    String traceId,
    Instant createdAt,
    Instant completedAt
) {

    public AiUsageRecord {
        fallbackChain =
            fallbackChain == null
                ? List.of()
                : List.copyOf(fallbackChain);
    }

    public static AiUsageRecord started(
        UUID id,
        AiExecutionCommand command,
        AiRoutePlan routePlan,
        Instant now
    ) {
        return new AiUsageRecord(
            id,
            command.executionContext()
                .operationId(),
            command.executionContext()
                .userId(),
            command.executionContext()
                .problemSessionId(),
            command.capability(),
            routePlan.routePolicyVersion(),
            routePlan.routeId(),
            routePlan.primary().provider(),
            routePlan.primary().model(),
            routePlan.promptId(),
            routePlan.promptVersion(),
            routePlan.schemaVersion(),
            null,
            null,
            Status.STARTED,
            null,
            false,
            0,
            false,
            routePlan.fallbackChain()
                .stream()
                .map(target ->
                    target.provider()
                        + ":"
                        + target.model()
                )
                .toList(),
            null,
            null,
            null,
            0,
            0,
            "USD",
            routePlan.pricingVersion(),
            0,
            0,
            command.executionContext()
                .correlationId(),
            command.executionContext()
                .traceId(),
            now,
            null
        );
    }

    public AiUsageRecord completed(
        AiExecutionResult result,
        AiRouteTarget lastTarget,
        Instant now
    ) {
        AiProvenance provenance =
            result.provenance();

        AiUsage usage =
            result.usage();

        String actualProvider =
            provenance != null
                ? provenance.provider()
                : lastTarget.provider();

        String actualModel =
            provenance != null
                ? provenance.model()
                : lastTarget.model();

        return new AiUsageRecord(
            id,
            operationId,
            userId,
            problemSessionId,
            capability,
            routePolicyVersion,
            routeId,
            actualProvider,
            actualModel,
            promptId,
            promptVersion,
            schemaVersion,
            provenance == null
                ? null
                : provenance.providerRequestId(),
            provenance == null
                ? null
                : provenance.providerResponseId(),
            Status.from(result.status()),
            result.failureClass(),
            result.retryable(),
            result.attemptCount(),
            result.fallbackUsed(),
            fallbackChain,
            usage == null
                ? null
                : usage.inputTokens(),
            usage == null
                ? null
                : usage.outputTokens(),
            usage == null
                ? null
                : usage.imageUnits(),
            usage == null
                ? 0
                : usage.requestUnits(),
            usage == null
                ? 0
                : usage.estimatedCostMicros(),
            usage == null
                ? "USD"
                : usage.currency(),
            pricingVersion,
            result.providerLatencyMs(),
            result.gatewayLatencyMs(),
            correlationId,
            traceId,
            createdAt,
            now
        );
    }

    public enum Status {
        STARTED,
        SUCCEEDED,
        FAILED_RETRYABLE,
        FAILED_TERMINAL,
        DISABLED,
        BLOCKED_BUDGET,
        BLOCKED_PROVIDER_UNAVAILABLE,
        BLOCKED_POLICY;

        static Status from(
            AiExecutionStatus status
        ) {
            return Status.valueOf(
                status.name()
            );
        }
    }
}
