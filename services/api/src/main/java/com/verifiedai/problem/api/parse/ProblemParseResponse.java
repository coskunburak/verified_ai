package com.verifiedai.problem.api.parse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.problem.application.parse.ProblemParseStatusResult;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

record ProblemParseResponse(
    UUID parseJobId,
    UUID problemSessionId,
    UUID recognitionEvidenceId,
    Integer recognitionEvidenceRevision,
    String jobStatus,
    String capability,
    int attemptCount,
    int maxAttempts,
    String lastErrorCode,
    String lastFailureClass,
    UUID problemParseId,
    Integer parseRevision,
    String supportStatus,
    String unsupportedReason,
    boolean reviewRequired,
    String schemaVersion,
    String promptId,
    String promptVersion,
    String routePolicyVersion,
    String provider,
    String model,
    Map<String, Object> normalizedProblem,
    Instant createdAt,
    Instant updatedAt,
    Instant completedAt
) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    static ProblemParseResponse from(ProblemParseStatusResult result) {
        return new ProblemParseResponse(
            result.parseJobId(),
            result.problemSessionId(),
            result.recognitionEvidenceId(),
            result.recognitionEvidenceRevision(),
            result.jobStatus(),
            result.capability(),
            result.attemptCount(),
            result.maxAttempts(),
            result.lastErrorCode(),
            result.lastFailureClass(),
            result.problemParseId(),
            result.parseRevision(),
            result.supportStatus(),
            result.unsupportedReason(),
            result.reviewRequired(),
            result.schemaVersion(),
            result.promptId(),
            result.promptVersion(),
            result.routePolicyVersion(),
            result.provider(),
            result.model(),
            parse(result.normalizedProblemJson()),
            result.createdAt(),
            result.updatedAt(),
            result.completedAt()
        );
    }

    private static Map<String, Object> parse(String normalizedProblemJson) {
        if (normalizedProblemJson == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(normalizedProblemJson, MAP_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored normalized problem parse is not valid JSON", exception);
        }
    }
}
