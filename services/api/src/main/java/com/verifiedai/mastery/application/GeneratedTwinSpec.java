package com.verifiedai.mastery.application;

import java.util.Objects;

public record GeneratedTwinSpec(
        String problemStatementLatex,
        String expectedCanonicalExpr,
        String solutionStepsJson,
        String generatorSeed,
        String verifierProofHash
) {
    public GeneratedTwinSpec {
        Objects.requireNonNull(problemStatementLatex, "problemStatementLatex cannot be null");
        Objects.requireNonNull(expectedCanonicalExpr, "expectedCanonicalExpr cannot be null");
        Objects.requireNonNull(solutionStepsJson, "solutionStepsJson cannot be null");
        Objects.requireNonNull(generatorSeed, "generatorSeed cannot be null");
        Objects.requireNonNull(verifierProofHash, "verifierProofHash cannot be null");
    }
}
