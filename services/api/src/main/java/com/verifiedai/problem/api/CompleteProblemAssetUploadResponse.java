package com.verifiedai.problem.api;

import com.verifiedai.problem.application.ProblemAssetUploadCompletionResult;
import java.time.Instant;
import java.util.UUID;

public record CompleteProblemAssetUploadResponse(
    UUID uploadId,
    UUID problemSessionId,
    UUID problemAssetId,
    String problemSessionStatus,
    String assetStatus,
    Instant availableAt
) {
    static CompleteProblemAssetUploadResponse from(ProblemAssetUploadCompletionResult result) {
        return new CompleteProblemAssetUploadResponse(
            result.uploadId(),
            result.problemSessionId(),
            result.problemAssetId(),
            result.problemSessionStatus(),
            result.assetStatus(),
            result.availableAt()
        );
    }
}
