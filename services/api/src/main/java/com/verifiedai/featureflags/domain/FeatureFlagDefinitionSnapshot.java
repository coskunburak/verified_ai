package com.verifiedai.featureflags.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable domain snapshot of a persisted feature flag or remote config definition.
 */
public record FeatureFlagDefinitionSnapshot(
    String flagKey,
    FlagCategory category,
    FlagValueType valueType,
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
    public FeatureFlagDefinitionSnapshot {
        Objects.requireNonNull(flagKey, "flagKey must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(valueType, "valueType must not be null");
        Objects.requireNonNull(bucketingSalt, "bucketingSalt must not be null");
        Objects.requireNonNull(defaultValueJson, "defaultValueJson must not be null");
        Objects.requireNonNull(currentValueJson, "currentValueJson must not be null");
        Objects.requireNonNull(owner, "owner must not be null");
        Objects.requireNonNull(purpose, "purpose must not be null");
    }
}
