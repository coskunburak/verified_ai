package com.verifiedai.ai.application;

import java.util.UUID;

public interface AiEntitlementResolver {
    String tier(UUID userId);

    static AiEntitlementResolver conservative() {
        return userId -> "FREE";
    }
}
