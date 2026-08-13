package com.verifiedai.problem.application.asset;

import java.time.Instant;
import java.util.UUID;

public record ProblemAssetUploadCompletionResult(
    UUID uploadId,
    UUID problemSessionId,
    UUID problemAssetId,
    String problemSessionStatus,
    String assetStatus,
    Instant availableAt
) {
}
