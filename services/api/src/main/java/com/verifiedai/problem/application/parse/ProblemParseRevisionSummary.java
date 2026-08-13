package com.verifiedai.problem.application.parse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProblemParseRevisionSummary(
    UUID id,
    int revision,
    String source,
    UUID parentParseId,
    boolean selected,
    String supportStatus,
    boolean reviewRequired,
    String correctionReason,
    List<String> correctedFieldCategories,
    Instant createdAt
) {
}
