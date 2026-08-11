package com.verifiedai.problem.application;

import java.time.Instant;
import java.util.UUID;

public record ProblemParseCorrectionResult(
    UUID problemSessionId,
    UUID problemParseId,
    int revision,
    String source,
    UUID parentParseId,
    boolean selected,
    String supportStatus,
    boolean reviewRequired,
    boolean canonicalizationRequired,
    Instant createdAt
) {
}
