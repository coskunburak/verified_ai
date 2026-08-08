package com.verifiedai.problem.domain.port;

import java.time.Duration;

public interface ProblemAssetStorage {
    PresignedProblemAssetUpload presignPut(String objectKey, String contentType, long sizeBytes, Duration ttl);

    ProblemAssetObjectMetadata head(String objectKey);

    String sha256Hex(String objectKey);

    void deleteIfExists(String objectKey);
}
