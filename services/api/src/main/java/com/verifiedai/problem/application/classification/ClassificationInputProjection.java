package com.verifiedai.problem.application.classification;

import java.util.List;

public record ClassificationInputProjection(
    String projectionVersion,
    String canonicalSchemaVersion,
    String problemType,
    String taskType,
    String normalizedText,
    String displayLatex,
    List<String> variables,
    int statementCount,
    int sourceConstraintCount,
    int derivedRestrictionCount,
    boolean upstreamReviewRequired
) {
    public ClassificationInputProjection {
        variables =
            variables == null
                ? List.of()
                : List.copyOf(variables);

        if (statementCount < 1) {
            throw new IllegalArgumentException(
                "Classification projection requires a canonical statement"
            );
        }

        if (
            sourceConstraintCount < 0
                || derivedRestrictionCount < 0
        ) {
            throw new IllegalArgumentException(
                "Classification projection counts cannot be negative"
            );
        }
    }
}
