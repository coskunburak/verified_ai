package com.verifiedai.ai.application;

public record AiProblemClassifyResult(
    String rawOutputJson,
    AiProvenance provenance,
    AiUsage usage,
    long providerLatencyMs
) {
}
