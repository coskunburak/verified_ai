package com.verifiedai.ai.infrastructure.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.ai.vision-recognition")
public record AiVisionRecognitionProperties(
    boolean enabled,
    String primaryProvider,
    String fallbackProvider,
    String routePolicyVersion,
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
