package com.verifiedai.ai.application;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public record AiRouteContext(
    AiCapability capability,
    String canonicalProblemType,
    String taskType,
    String difficulty,
    Double classificationConfidence,
    Boolean parserReviewRequired,
    String recognitionQualityRisk,
    Boolean verificationMethodAvailable,
    String entitlementTier,
    Locale locale,
    Duration latencyBudget,
    Long maxCostMicros,
    String providerHealthSnapshot,
    Map<String, String> featureFlagContext,
    AiProviderFailureClass priorRouteFailure,
    String privacyRegion,
    String privacyRetentionConstraint,
    RouteRole routeRole
) {

    public AiRouteContext {
        Objects.requireNonNull(
            capability,
            "capability is required"
        );

        locale =
            locale == null
                ? Locale.ENGLISH
                : locale;

        featureFlagContext =
            featureFlagContext == null
                ? Map.of()
                : Map.copyOf(featureFlagContext);

        if (
            classificationConfidence != null
                && (
                classificationConfidence < 0.0
                    || classificationConfidence > 1.0
            )
        ) {
            throw new IllegalArgumentException(
                "classificationConfidence must be within [0,1]"
            );
        }

        if (
            latencyBudget != null
                && (
                latencyBudget.isZero()
                    || latencyBudget.isNegative()
            )
        ) {
            throw new IllegalArgumentException(
                "latencyBudget must be positive"
            );
        }

        if (
            maxCostMicros != null
                && maxCostMicros < 0
        ) {
            throw new IllegalArgumentException(
                "maxCostMicros must not be negative"
            );
        }

        routeRole =
            routeRole == null
                ? RouteRole.DEFAULT
                : routeRole;
    }

    public static AiRouteContext basic(
        AiCapability capability,
        Duration latencyBudget
    ) {
        return new AiRouteContext(
            capability,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Locale.ENGLISH,
            latencyBudget,
            null,
            null,
            Map.of(),
            null,
            null,
            null,
            RouteRole.DEFAULT
        );
    }

    public enum RouteRole {
        DEFAULT,
        PRIMARY_SOLVER,
        SECONDARY_SOLVER,
        ARBITRATOR
    }
}
