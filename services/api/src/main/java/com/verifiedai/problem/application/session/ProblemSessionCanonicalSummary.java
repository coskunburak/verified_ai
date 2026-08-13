package com.verifiedai.problem.application.session;

import java.util.UUID;

public record ProblemSessionCanonicalSummary(
    UUID canonicalProblemId,
    int canonicalRevision,
    UUID problemParseId,
    int problemParseRevision,
    String problemType,
    String taskType
) {
}
