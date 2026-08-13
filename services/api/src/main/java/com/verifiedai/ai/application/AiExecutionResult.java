package com.verifiedai.ai.application;

import java.util.UUID;

public record AiExecutionResult(
    UUID operationId,
    AiCapability capability,
    AiExecutionStatus status,
    AiCapabilityResult output,
    AiRoutePlan routePlan,
    AiProvenance provenance,
    AiUsage usage,
    long providerLatencyMs,
    long gatewayLatencyMs,
    int attemptCount,
    boolean fallbackUsed,
    AiProviderFailureClass failureClass,
    boolean retryable,
    UUID ledgerRecordId
) {

    public AiExecutionResult {
        if (operationId == null) {
            throw new IllegalArgumentException(
                "operationId is required"
            );
        }

        if (capability == null) {
            throw new IllegalArgumentException(
                "capability is required"
            );
        }

        if (status == null) {
            throw new IllegalArgumentException(
                "status is required"
            );
        }

        if (providerLatencyMs < 0) {
            throw new IllegalArgumentException(
                "providerLatencyMs must not be negative"
            );
        }

        if (gatewayLatencyMs < 0) {
            throw new IllegalArgumentException(
                "gatewayLatencyMs must not be negative"
            );
        }

        if (attemptCount < 0) {
            throw new IllegalArgumentException(
                "attemptCount must not be negative"
            );
        }

        if (
            status == AiExecutionStatus.SUCCEEDED
                && (
                output == null
                    || provenance == null
                    || usage == null
            )
        ) {
            throw new IllegalArgumentException(
                "Successful AI result requires output, provenance and usage"
            );
        }
    }

    public boolean succeeded() {
        return status == AiExecutionStatus.SUCCEEDED;
    }
}
