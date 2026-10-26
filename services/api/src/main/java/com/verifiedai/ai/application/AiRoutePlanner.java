package com.verifiedai.ai.application;

import java.util.Map;

public interface AiRoutePlanner {

    boolean enabled(AiCapability capability);

    AiRoutePlan routePlan(
        AiRouteContext context
    );

    default AiRoutePlan routePlan(AiRouteContext context, java.util.UUID userId) {
        return routePlan(context);
    }

    default long attemptCeiling(AiRoutePlan plan, AiRouteTarget target) {
        return plan.maxCostMicros();
    }

    default void checkAttempt(AiRouteContext context, AiRoutePlan plan, AiRouteTarget target) { }

    Map<AiCapability, AiRoutePolicy> policies();
}
