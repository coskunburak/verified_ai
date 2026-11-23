package com.verifiedai.featureflags.api.dto;

public record FeatureFlagKillSwitchRequest(
    Boolean active,
    String reason,
    Long expectedVersion,
    Boolean trainingEligible
) {
}
