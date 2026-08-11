package com.verifiedai.problem.api;

import com.verifiedai.problem.application.ProblemSessionCanonicalSummary;
import java.util.UUID;

public record ProblemSessionCanonicalSummaryResponse(
    UUID canonicalProblemId,
    int canonicalRevision,
    UUID problemParseId,
    int problemParseRevision,
    String problemType,
    String taskType
) {
    static ProblemSessionCanonicalSummaryResponse from(ProblemSessionCanonicalSummary canonicalProblem) {
        if (canonicalProblem == null) {
            return null;
        }
        return new ProblemSessionCanonicalSummaryResponse(
            canonicalProblem.canonicalProblemId(),
            canonicalProblem.canonicalRevision(),
            canonicalProblem.problemParseId(),
            canonicalProblem.problemParseRevision(),
            canonicalProblem.problemType(),
            canonicalProblem.taskType()
        );
    }
}
