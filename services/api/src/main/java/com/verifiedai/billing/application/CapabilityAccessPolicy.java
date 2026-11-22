package com.verifiedai.billing.application;

import java.util.UUID;

public interface CapabilityAccessPolicy {
    default CapabilityDecision evaluate(UUID userId, LearningOsCapability capability) {
        boolean allowed = check(userId, capability);
        return new CapabilityDecision(allowed, capability, "UNKNOWN", "UNKNOWN", allowed ? "NONE" : "TIER_REQUIRED", true);
    }

    default boolean check(UUID userId, LearningOsCapability capability) {
        throw new IllegalStateException("Learning OS capability policy unavailable");
    }

    default void require(UUID userId, LearningOsCapability capability) {
        if (!check(userId, capability)) {
            throw new com.verifiedai.sharedkernel.error.ApiProblemException(org.springframework.http.HttpStatus.FORBIDDEN,
                com.verifiedai.sharedkernel.error.ApiErrorCode.ENTITLEMENT_REQUIRED, "Plan required for this action", true, "UPGRADE");
        }
    }

    void requireBasicSolve(UUID userId);
}
