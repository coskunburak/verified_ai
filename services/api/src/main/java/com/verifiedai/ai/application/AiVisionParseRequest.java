package com.verifiedai.ai.application;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public record AiVisionParseRequest(
    UUID problemSessionId,
    UUID sourceAssetId,
    UUID inputAssetId,
    String contentType,
    byte[] imageBytes,
    int width,
    int height,
    String promptId,
    String promptVersion,
    String schemaVersion,
    Duration timeout,
    List<String> upstreamQualityWarnings
) {
}
