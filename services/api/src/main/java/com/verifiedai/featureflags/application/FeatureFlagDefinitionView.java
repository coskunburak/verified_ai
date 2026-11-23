package com.verifiedai.featureflags.application;

import java.time.Instant;

public record FeatureFlagDefinitionView(
    String flagKey,
    String category,
    String valueType,
    boolean clientSafe,
    boolean enabled,
    boolean killSwitchActive,
    int rolloutBasisPoints,
    String bucketingSalt,
    String defaultValueJson,
    String currentValueJson,
    String owner,
    String purpose,
    Instant plannedRemovalAt,
    long version,
    boolean trainingEligible,
    Instant createdAt,
    Instant updatedAt
) {
}
