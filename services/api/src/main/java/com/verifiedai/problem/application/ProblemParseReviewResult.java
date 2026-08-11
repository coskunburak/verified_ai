package com.verifiedai.problem.application;

import java.time.Instant;
import java.util.UUID;

public record ProblemParseReviewResult(
    UUID problemSessionId,
    CurrentParse currentParse,
    long revisionCount,
    boolean canCorrect
) {
    public record CurrentParse(
        UUID problemParseId,
        int revision,
        String source,
        String supportStatus,
        boolean reviewRequired,
        String normalizedProblemJson,
        Instant createdAt
    ) {
    }
}
