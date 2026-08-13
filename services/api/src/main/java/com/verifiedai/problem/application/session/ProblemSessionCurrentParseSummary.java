package com.verifiedai.problem.application.session;

import java.util.UUID;

public record ProblemSessionCurrentParseSummary(
    UUID parseId,
    int revision,
    String source,
    String supportStatus,
    boolean reviewRequired
) {
}
