package com.verifiedai.ai.application;

import java.time.Duration;

public record AiRoutePlan(
    AiCapability capability,
    String routePolicyVersion,
    String primaryProvider,
    String fallbackProvider,
    String promptId,
    String promptVersion,
    String schemaVersion,
    Duration timeout,
    int maxAttempts,
    int maxResponseBytes,
    long maxCostMicros,
    String pricingVersion
) {
}
