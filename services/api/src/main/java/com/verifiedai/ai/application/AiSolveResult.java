package com.verifiedai.ai.application;

public record AiSolveResult(String rawOutputJson, AiProvenance provenance, AiUsage usage,
    long providerLatencyMs) implements AiCapabilityResult {
    @Override public AiSolveResult withExecutionMetadata(AiProvenance value, long latency) {
        return new AiSolveResult(rawOutputJson, value, usage, latency);
    }
}
