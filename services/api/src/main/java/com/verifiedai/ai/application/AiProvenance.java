package com.verifiedai.ai.application;

public record AiProvenance(
    String provider,
    String model,
    String routePolicyVersion,
    String promptId,
    String promptVersion,
    String schemaVersion,
    String providerRequestId,
    String providerResponseId,
    boolean fallbackUsed
) {
}
