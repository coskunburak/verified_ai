package com.verifiedai.ai.application;

public record AiVisionParseResult(
    String rawOutputJson,
    AiProvenance provenance,
    AiUsage usage,
    long providerLatencyMs
) implements AiCapabilityResult {

    @Override
    public AiVisionParseResult withExecutionMetadata(
        AiProvenance provenance,
        long providerLatencyMs
    ) {
        return new AiVisionParseResult(
            rawOutputJson,
            provenance,
            usage,
            providerLatencyMs
        );
    }
}
