package com.verifiedai.ai.application;

import java.util.Objects;

public record AiExecutionCommand(
    AiCapability capability,
    AiCapabilityRequest request,
    AiExecutionContext executionContext,
    AiRouteContext routeContext,
    String inputPayloadContentType,
    String inputPayloadSchemaVersion,
    String expectedPromptId,
    String expectedPromptVersion,
    String expectedOutputSchemaVersion,
    Long maxCostMicros
) {

    public AiExecutionCommand {
        Objects.requireNonNull(
            capability,
            "capability is required"
        );
        Objects.requireNonNull(
            request,
            "request is required"
        );
        Objects.requireNonNull(
            executionContext,
            "executionContext is required"
        );
        Objects.requireNonNull(
            routeContext,
            "routeContext is required"
        );

        if (routeContext.capability() != capability) {
            throw new IllegalArgumentException(
                "Route context capability mismatch"
            );
        }

        if (
            inputPayloadContentType == null
                || inputPayloadContentType.isBlank()
        ) {
            throw new IllegalArgumentException(
                "inputPayloadContentType is required"
            );
        }

        if (
            maxCostMicros != null
                && maxCostMicros < 0
        ) {
            throw new IllegalArgumentException(
                "maxCostMicros must not be negative"
            );
        }
    }
}
