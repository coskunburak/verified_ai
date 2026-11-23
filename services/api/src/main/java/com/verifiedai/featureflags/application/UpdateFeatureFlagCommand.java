package com.verifiedai.featureflags.application;

import java.time.Instant;

public record UpdateFeatureFlagCommand(
    boolean enabled,
    int rolloutBasisPoints,
    String currentValueJson,
    Instant plannedRemovalAt,
    String reason,
    long expectedVersion,
    String idempotencyKey
) {
}
