package com.verifiedai.featureflags.application;

public record UpdateExperimentCommand(
    String status,
    int rolloutBasisPoints,
    String reason,
    long expectedVersion,
    String idempotencyKey
) {
}
