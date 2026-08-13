package com.verifiedai.ai.application;

import java.util.Map;

public interface AiRoutePlanner {

    boolean enabled(AiCapability capability);

    AiRoutePlan routePlan(
        AiRouteContext context
    );

    Map<AiCapability, AiRoutePolicy> policies();
}
