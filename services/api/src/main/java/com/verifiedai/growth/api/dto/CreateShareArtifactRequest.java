package com.verifiedai.growth.api.dto;

import java.util.Map;

public record CreateShareArtifactRequest(
    String artifactType,
    String locale,
    String displayAlias,
    String referralCode,
    Map<String, Object> summaryPayload,
    Integer ttlDays,
    Boolean trainingEligible
) {
}
