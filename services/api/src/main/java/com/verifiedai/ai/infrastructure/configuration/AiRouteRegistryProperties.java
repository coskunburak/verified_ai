package com.verifiedai.ai.infrastructure.configuration;

import com.verifiedai.ai.application.AiCapability;
import com.verifiedai.ai.application.AiRoutePlan;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.ai")
public record AiRouteRegistryProperties(
    UsageLedger usageLedger,
    Map<AiCapability, CapabilityRoute> capabilities
) {

    public AiRouteRegistryProperties {
        usageLedger =
            usageLedger == null
                ? new UsageLedger(true)
                : usageLedger;

        capabilities =
            capabilities == null
                ? Map.of()
                : Map.copyOf(capabilities);
    }

    public record UsageLedger(
        boolean enabled
    ) {
    }

    public record CapabilityRoute(
        boolean enabled,
        String routePolicyVersion,
        String routeId,
        RouteTarget primary,
        List<RouteTarget> fallbacks,
        String promptId,
        String promptVersion,
        String schemaVersion,
        Duration timeout,
        int maxAttempts,
        int maxResponseBytes,
        long maxCostMicros,
        String pricingVersion,
        AiRoutePlan.CachePolicy cachePolicy,
        AiRoutePlan.StreamingPolicy streamingPolicy,
        String providerRetentionPolicy,
        String privacyRegion,
        String circuitBreakerKey,
        String qualityGateVersion,
        AiRoutePlan.ReleaseStage releaseStage
    ) {

        public CapabilityRoute {
            fallbacks =
                fallbacks == null
                    ? List.of()
                    : List.copyOf(fallbacks);
        }
    }

    public record RouteTarget(
        String provider,
        String model
    ) {
    }
}
