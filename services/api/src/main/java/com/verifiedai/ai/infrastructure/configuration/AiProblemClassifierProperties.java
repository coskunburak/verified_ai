package com.verifiedai.ai.infrastructure.configuration;

import com.verifiedai.ai.application.AiRoutePlan;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.ai.problem-classifier")
public record AiProblemClassifierProperties(
    boolean enabled,
    String primaryProvider,
    String primaryModel,
    String fallbackProvider,
    String fallbackModel,
    String routePolicyVersion,
    String routeId,
    String promptId,
    String promptVersion,
    String schemaVersion,
    Duration timeout,
    int maxAttempts,
    int maxResponseBytes,
    long maxCostMicros,
    String pricingVersion,
    AiRoutePlan.ReleaseStage releaseStage
) {
}
