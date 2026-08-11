package com.verifiedai.problem.application;

import java.util.UUID;

public record ProblemParseCorrectionCommand(
    UUID userId,
    UUID problemSessionId,
    UUID baseParseId,
    int baseRevision,
    String idempotencyKey,
    String correctionReason,
    String correctedProblemJson
) {
}
