package com.verifiedai.ai.application;

import java.util.Objects;

public record AiRoutePolicy(
    AiCapability capability,
    boolean enabled,
    AiRoutePlan routePlan
) {

    public AiRoutePolicy {
        Objects.requireNonNull(
            capability,
            "capability is required"
        );

        if (enabled && routePlan == null) {
            throw new IllegalArgumentException(
                "Enabled AI capability requires a route plan"
            );
        }

        if (
            routePlan != null
                && routePlan.capability() != capability
        ) {
            throw new IllegalArgumentException(
                "Route plan capability does not match policy"
            );
        }
    }

    public static AiRoutePolicy disabled(
        AiCapability capability
    ) {
        return new AiRoutePolicy(
            capability,
            false,
            null
        );
    }

    public static AiRoutePolicy enabled(
        AiRoutePlan routePlan
    ) {
        return new AiRoutePolicy(
            routePlan.capability(),
            true,
            routePlan
        );
    }
}
