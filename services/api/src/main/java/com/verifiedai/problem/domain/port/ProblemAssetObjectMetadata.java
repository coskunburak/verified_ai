package com.verifiedai.problem.domain.port;

public record ProblemAssetObjectMetadata(
    long sizeBytes,
    String contentType
) {
}
