package com.verifiedai.problem.api;

public record PresignProblemAssetUploadRequest(
    String source,
    String assetKind,
    String contentType,
    Long sizeBytes,
    String checksumSha256,
    Integer imageWidth,
    Integer imageHeight,
    Integer pageCount,
    Double cropX,
    Double cropY,
    Double cropWidth,
    Double cropHeight
) {
    long safeSizeBytes() {
        return sizeBytes == null ? -1 : sizeBytes;
    }
}
