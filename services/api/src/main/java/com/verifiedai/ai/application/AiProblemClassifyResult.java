package com.verifiedai.ai.application;

public record AiProblemClassifyResult(
    String rawOutputJson,
    AiProvenance provenance,
    AiUsage usage,
    long providerLatencyMs
) implements AiCapabilityResult {

    @Override
    public AiProblemClassifyResult withExecutionMetadata(
        AiProvenance provenance,
        long providerLatencyMs
    ) {
        return new AiProblemClassifyResult(
            rawOutputJson,
            provenance,
            usage,
            providerLatencyMs
        );
    }
}
