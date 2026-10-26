package com.verifiedai.ai.application;

import java.util.Objects;
import java.util.UUID;

public record AiSolveRequest(UUID canonicalProblemId, UUID problemParseId, int problemParseRevision,
    String canonicalProblemJson) implements AiCapabilityRequest {
    public AiSolveRequest {
        Objects.requireNonNull(canonicalProblemId); Objects.requireNonNull(problemParseId);
        if (problemParseRevision<1 || canonicalProblemJson==null
            || canonicalProblemJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>65536)
            throw new IllegalArgumentException("Invalid bounded solve input");
    }
}
