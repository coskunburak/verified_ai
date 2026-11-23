package com.verifiedai.featureflags.api.dto;

public record ExperimentUpdateRequest(
    String status,
    Integer rolloutBasisPoints,
    String reason,
    Long expectedVersion,
    Boolean trainingEligible
) {
}
