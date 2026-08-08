package com.verifiedai.problem.domain.port;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

public record PresignedProblemAssetUpload(
    URI uploadUrl,
    Instant expiresAt,
    Map<String, String> requiredHeaders
) {
    public PresignedProblemAssetUpload {
        requiredHeaders = Map.copyOf(requiredHeaders);
    }
}
