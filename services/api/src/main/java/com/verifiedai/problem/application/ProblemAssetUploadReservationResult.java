package com.verifiedai.problem.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ProblemAssetUploadReservationResult(
    UUID uploadId,
    UUID problemSessionId,
    UUID problemAssetId,
    String assetStatus,
    String uploadUrl,
    Instant expiresAt,
    Map<String, String> requiredHeaders
) {
    public ProblemAssetUploadReservationResult {
        requiredHeaders = Map.copyOf(requiredHeaders);
    }
}
