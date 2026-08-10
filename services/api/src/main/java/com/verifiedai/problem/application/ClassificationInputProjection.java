package com.verifiedai.problem.application;

import java.util.List;

public record ClassificationInputProjection(
    String problemType,
    String taskType,
    String normalizedText,
    String displayLatex,
    List<String> variables,
    int constraintCount,
    String ontologyVersion
) {
    public ClassificationInputProjection {
        variables = List.copyOf(variables);
    }
}
