package com.verifiedai.ai.application;

public record AiVisionParseResult(
    String rawOutputJson,
    AiProvenance provenance,
    AiUsage usage,
    long providerLatencyMs
) {
}
