package com.verifiedai.ai.application;

public interface AiCapabilityResult {

    String rawOutputJson();

    AiProvenance provenance();

    AiUsage usage();

    long providerLatencyMs();

    AiCapabilityResult withExecutionMetadata(
        AiProvenance provenance,
        long providerLatencyMs
    );
}
