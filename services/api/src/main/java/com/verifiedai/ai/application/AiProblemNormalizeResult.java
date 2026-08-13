package com.verifiedai.ai.application;

public record AiProblemNormalizeResult(
    String rawOutputJson,
    AiProvenance provenance,
    AiUsage usage,
    long providerLatencyMs
) implements AiCapabilityResult {

    @Override
    public AiProblemNormalizeResult withExecutionMetadata(
        AiProvenance provenance,
        long providerLatencyMs
    ) {
        return new AiProblemNormalizeResult(
            rawOutputJson,
            provenance,
            usage,
            providerLatencyMs
        );
    }
}
