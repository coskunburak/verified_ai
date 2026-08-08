package com.verifiedai.problem.api;

import com.verifiedai.problem.application.ProblemAssetUploadReservationResult;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PresignProblemAssetUploadResponse(
    UUID uploadId,
    UUID problemSessionId,
    UUID problemAssetId,
    String assetStatus,
    String uploadUrl,
    Instant expiresAt,
    Map<String, String> requiredHeaders
) {
    static PresignProblemAssetUploadResponse from(ProblemAssetUploadReservationResult result) {
        return new PresignProblemAssetUploadResponse(
            result.uploadId(),
            result.problemSessionId(),
            result.problemAssetId(),
            result.assetStatus(),
            result.uploadUrl(),
            result.expiresAt(),
            result.requiredHeaders()
        );
    }
}
