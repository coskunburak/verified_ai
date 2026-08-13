package com.verifiedai.ai.application;

import java.time.Duration;
import java.util.UUID;

public record AiProblemNormalizeRequest(
    UUID problemSessionId,
    UUID recognitionEvidenceId,
    int recognitionEvidenceRevision,
    String normalizedRecognitionEvidenceJson,
    String upstreamQualityEvidenceJson,
    String promptId,
    String promptVersion,
    String schemaVersion,
    Duration timeout
)implements AiCapabilityRequest {
}
