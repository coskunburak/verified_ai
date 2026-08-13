package com.verifiedai.ai.application;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record AiRoutePlan(
    AiCapability capability,
    String routePolicyVersion,
    String routeId,
    AiRouteTarget primary,
    List<AiRouteTarget> fallbackChain,
    String promptId,
    String promptVersion,
    String schemaVersion,
    Duration timeout,
    int maxAttempts,
    int maxResponseBytes,
    long maxCostMicros,
    String pricingVersion,
    CachePolicy cachePolicy,
    StreamingPolicy streamingPolicy,
    String providerRetentionPolicy,
    String privacyRegion,
    String circuitBreakerKey,
    String qualityGateVersion,
    ReleaseStage releaseStage
) {

    public AiRoutePlan(
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
        this(
            capability,
            routePolicyVersion,
            capability.name()
                .toLowerCase(Locale.ROOT)
                + "-compat-v1",
            new AiRouteTarget(
                primaryProvider,
                primaryProvider
            ),
            legacyFallbacks(fallbackProvider),
            promptId,
            promptVersion,
            schemaVersion,
            timeout,
            maxAttempts,
            maxResponseBytes,
            maxCostMicros,
            pricingVersion,
            CachePolicy.DISABLED,
            StreamingPolicy.DISABLED,
            "NO_TRAINING_BY_DEFAULT",
            "UNSPECIFIED",
            capability.name()
                + ":"
                + primaryProvider,
            null,
            ReleaseStage.LOCAL_ONLY
        );
    }

    public AiRoutePlan {
        Objects.requireNonNull(
            capability,
            "capability is required"
        );

        requireText(
            routePolicyVersion,
            "routePolicyVersion"
        );
        requireText(routeId, "routeId");

        Objects.requireNonNull(
            primary,
            "primary route is required"
        );

        fallbackChain =
            fallbackChain == null
                ? List.of()
                : List.copyOf(fallbackChain);

        requireText(promptId, "promptId");
        requireText(promptVersion, "promptVersion");
        requireText(schemaVersion, "schemaVersion");

        if (
            timeout == null
                || timeout.isZero()
                || timeout.isNegative()
        ) {
            throw new IllegalArgumentException(
                "timeout must be positive"
            );
        }

        if (maxAttempts < 1) {
            throw new IllegalArgumentException(
                "maxAttempts must be >= 1"
            );
        }

        if (maxResponseBytes <= 0) {
            throw new IllegalArgumentException(
                "maxResponseBytes must be > 0"
            );
        }

        if (maxCostMicros < 0) {
            throw new IllegalArgumentException(
                "maxCostMicros must be >= 0"
            );
        }

        requireText(
            pricingVersion,
            "pricingVersion"
        );

        cachePolicy =
            cachePolicy == null
                ? CachePolicy.DISABLED
                : cachePolicy;

        streamingPolicy =
            streamingPolicy == null
                ? StreamingPolicy.DISABLED
                : streamingPolicy;

        requireText(
            providerRetentionPolicy,
            "providerRetentionPolicy"
        );
        requireText(
            privacyRegion,
            "privacyRegion"
        );
        requireText(
            circuitBreakerKey,
            "circuitBreakerKey"
        );

        Objects.requireNonNull(
            releaseStage,
            "releaseStage is required"
        );
    }

    /*
     * Compatibility accessors used by existing Phase 4 callers.
     */
    public String primaryProvider() {
        return primary.provider();
    }

    public String primaryModel() {
        return primary.model();
    }

    public String fallbackProvider() {
        return fallbackChain.isEmpty()
            ? null
            : fallbackChain.getFirst().provider();
    }

    public String fallbackModel() {
        return fallbackChain.isEmpty()
            ? null
            : fallbackChain.getFirst().model();
    }

    public List<AiRouteTarget> allTargets() {
        List<AiRouteTarget> targets =
            new ArrayList<>(
                1 + fallbackChain.size()
            );

        targets.add(primary);
        targets.addAll(fallbackChain);

        return List.copyOf(targets);
    }

    public boolean containsProvider(
        String provider
    ) {
        if (provider == null || provider.isBlank()) {
            return false;
        }

        String normalized =
            provider
                .trim()
                .toUpperCase(Locale.ROOT);

        return allTargets()
            .stream()
            .anyMatch(target ->
                target.provider().equals(normalized)
            );
    }

    public enum CachePolicy {
        DISABLED,
        ALLOWED
    }

    public enum StreamingPolicy {
        DISABLED,
        ALLOWED
    }

    public enum ReleaseStage {
        LOCAL_ONLY,
        STAGING,
        PRODUCTION,
        DISABLED
    }

    private static void requireText(
        String value,
        String field
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                field + " is required"
            );
        }
    }

    private static List<AiRouteTarget> legacyFallbacks(
        String fallbackProvider
    ) {
        if (
            fallbackProvider == null
                || fallbackProvider.isBlank()
        ) {
            return List.of();
        }

        return List.of(
            new AiRouteTarget(
                fallbackProvider,
                fallbackProvider
            )
        );
    }
}
