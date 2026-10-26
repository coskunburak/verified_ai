package com.verifiedai.ai.application;

import java.time.Duration;
import java.util.Set;

/** Server-owned approval for an exact model and an existing prompt-bound route. */
public record AiRouteEligibility(
    AiCapability capability, AiRouteTarget target, String qualityGateVersion,
    long attemptCeilingMicros, Duration latencyAllowance,
    Set<String> taskTypes, Set<String> difficulties, Set<String> entitlementTiers,
    String privacyRegion, String retentionPolicy, boolean approved
) {
    public AiRouteEligibility {
        if (capability == null || target == null || qualityGateVersion == null
            || qualityGateVersion.isBlank() || attemptCeilingMicros < 0
            || latencyAllowance == null || latencyAllowance.isNegative() || latencyAllowance.isZero()
            || privacyRegion == null || privacyRegion.isBlank()
            || retentionPolicy == null || !Set.of("NO_TRAINING_BY_DEFAULT", "ZERO_RETENTION").contains(retentionPolicy)) {
            throw new IllegalArgumentException("Invalid AI eligibility configuration");
        }
        taskTypes = Set.copyOf(taskTypes);
        difficulties = Set.copyOf(difficulties);
        entitlementTiers = Set.copyOf(entitlementTiers);
        if (taskTypes.isEmpty() || difficulties.isEmpty() || entitlementTiers.isEmpty()
            || !Set.of("UNKNOWN", "EASY", "MEDIUM", "HARD").containsAll(difficulties)
            || !Set.of("FREE", "PRO", "PRO_PLUS").containsAll(entitlementTiers)) {
            throw new IllegalArgumentException("Invalid AI eligibility scope");
        }
    }
}
