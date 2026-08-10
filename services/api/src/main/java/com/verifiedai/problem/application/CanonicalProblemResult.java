package com.verifiedai.problem.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CanonicalProblemResult(
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
    public CanonicalProblemResult {
        variables = List.copyOf(variables);
    }
}
