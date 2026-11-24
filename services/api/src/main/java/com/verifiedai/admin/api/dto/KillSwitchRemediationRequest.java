package com.verifiedai.admin.api.dto;

public record KillSwitchRemediationRequest(
    String flagKey,
    Boolean active,
    String reason,
    Long expectedVersion,
    Boolean trainingEligible
) {
}
