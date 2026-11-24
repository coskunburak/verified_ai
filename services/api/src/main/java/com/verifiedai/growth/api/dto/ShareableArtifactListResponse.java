package com.verifiedai.growth.api.dto;

import java.util.List;

public record ShareableArtifactListResponse(
    List<ShareableArtifactResponse> items,
    int totalCount,
    boolean trainingEligible,
    int llmTokensUsed
) {
}
