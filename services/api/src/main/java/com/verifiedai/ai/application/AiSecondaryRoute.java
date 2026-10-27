package com.verifiedai.ai.application;

/** Separately governed route. Implementations must never inherit the primary target. */
public interface AiSecondaryRoute {
    AiRoutePlan plan(AiRouteContext context);
    long attemptCeiling(AiRoutePlan plan, AiRouteTarget target);
}
