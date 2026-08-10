package com.verifiedai.ai.application;

import java.time.Duration;
import java.util.UUID;

public record AiProblemClassifyRequest(
    UUID canonicalProblemId,
    UUID problemSessionId,
    String problemType,
    String taskType,
    String classificationProjectionJson,
    String ontologyVersion,
    String candidateSkillsJson,
    String promptId,
    String promptVersion,
    String schemaVersion,
    Duration timeout
) {
}
