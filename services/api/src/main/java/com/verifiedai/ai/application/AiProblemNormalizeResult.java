package com.verifiedai.ai.application;

public record AiProblemNormalizeResult(
    String rawOutputJson,
    AiProvenance provenance,
    AiUsage usage,
    long providerLatencyMs
) {
}
