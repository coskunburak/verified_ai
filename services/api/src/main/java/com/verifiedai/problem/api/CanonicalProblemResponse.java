package com.verifiedai.problem.api;

import com.verifiedai.problem.application.CanonicalProblemResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

record CanonicalProblemResponse(
    UUID canonicalProblemId,
    UUID problemSessionId,
    UUID problemParseId,
    int problemParseRevision,
    int canonicalRevision,
    String schemaVersion,
    String verifierSchemaVersion,
    String problemType,
    String taskType,
    String normalizedText,
    String displayLatex,
    List<String> variables,
    int sourceConstraintCount,
    int derivedRestrictionCount,
    Instant createdAt
) {
    static CanonicalProblemResponse from(CanonicalProblemResult result) {
        return new CanonicalProblemResponse(
            result.canonicalProblemId(),
            result.problemSessionId(),
            result.problemParseId(),
            result.problemParseRevision(),
            result.canonicalRevision(),
            result.schemaVersion(),
            result.verifierSchemaVersion(),
            result.problemType(),
            result.taskType(),
            result.normalizedText(),
            result.displayLatex(),
            result.variables(),
            result.sourceConstraintCount(),
            result.derivedRestrictionCount(),
            result.createdAt()
        );
    }
}
