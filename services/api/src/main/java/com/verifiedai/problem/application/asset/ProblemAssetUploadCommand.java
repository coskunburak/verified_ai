package com.verifiedai.problem.application.asset;

public record ProblemAssetUploadCommand(
    String source,
    String assetKind,
    String contentType,
    long sizeBytes,
    String checksumSha256,
    Integer imageWidth,
    Integer imageHeight,
    Integer pageCount,
    Double cropX,
    Double cropY,
    Double cropWidth,
    Double cropHeight
) {
}
