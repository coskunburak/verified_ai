package com.verifiedai.problem.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.problem.application.ProblemParseCorrectionCommand;
import java.util.Map;
import java.util.UUID;

record CreateProblemParseCorrectionRequest(
    UUID baseParseId,
    Integer baseRevision,
    String correctionReason,
    Map<String, Object> problem
) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    ProblemParseCorrectionCommand toCommand(UUID userId, UUID problemSessionId, String idempotencyKey) {
        return new ProblemParseCorrectionCommand(
            userId,
            problemSessionId,
            baseParseId,
            baseRevision == null ? 0 : baseRevision,
            idempotencyKey,
            correctionReason,
            problemJson()
        );
    }

    private String problemJson() {
        if (problem == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(problem);
        } catch (Exception exception) {
            throw new IllegalStateException("Correction problem JSON cannot be serialized", exception);
        }
    }
}
