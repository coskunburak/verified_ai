package com.verifiedai.featureflags.api.dto;

import java.time.Instant;

public record FeatureFlagUpdateRequest(
    Boolean enabled,
    Integer rolloutBasisPoints,
    String currentValueJson,
    Instant plannedRemovalAt,
    String reason,
    Long expectedVersion,
    Boolean trainingEligible
) {
}
