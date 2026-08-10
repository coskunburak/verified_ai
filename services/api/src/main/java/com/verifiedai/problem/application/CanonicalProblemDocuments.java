package com.verifiedai.problem.application;

import java.util.List;
import java.util.UUID;

record CanonicalSourceParse(
    UUID problemParseId,
    int problemParseRevision,
    String problemParseSchemaVersion,
    String source
) {
}

record CanonicalVariable(
    String symbol,
    String role,
    String domain,
    List<String> sourceBlockIds
) {
    CanonicalVariable {
        sourceBlockIds = List.copyOf(sourceBlockIds);
    }
}

record CanonicalStatement(
    String kind,
    String sourceExpressionId,
    String relation,
    CanonicalExpressionNode left,
    CanonicalExpressionNode right,
    CanonicalExpressionNode expression,
    List<String> sourceBlockIds
) {
    CanonicalStatement {
        sourceBlockIds = List.copyOf(sourceBlockIds);
    }

    static CanonicalStatement expression(
        String sourceExpressionId,
        CanonicalExpressionNode expression,
        List<String> sourceBlockIds
    ) {
        return new CanonicalStatement("EXPRESSION", sourceExpressionId, null, null, null, expression, sourceBlockIds);
    }

    static CanonicalStatement relation(
        String sourceExpressionId,
        String relation,
        CanonicalExpressionNode left,
        CanonicalExpressionNode right,
        List<String> sourceBlockIds
    ) {
        return new CanonicalStatement("RELATION", sourceExpressionId, relation, left, right, null, sourceBlockIds);
    }
}

record CanonicalRestriction(
    String id,
    String relation,
    CanonicalExpressionNode left,
    CanonicalExpressionNode right,
    String reason,
    String provenance,
    List<String> sourceBlockIds
) {
    CanonicalRestriction {
        sourceBlockIds = List.copyOf(sourceBlockIds);
    }
}

record CanonicalSourceAssumption(
    String id,
    String text,
    String provenance,
    List<String> sourceBlockIds
) {
    CanonicalSourceAssumption {
        sourceBlockIds = List.copyOf(sourceBlockIds);
    }
}

record CanonicalComplexityPolicy(
    String version,
    int maxExpressionLength,
    int maxAstNodes,
    int maxAstDepth,
    int maxExponentMagnitude,
    int maxNumericDigits,
    int maxFunctionNestingDepth
) {
    static CanonicalComplexityPolicy from(CanonicalMathLimits limits) {
        return new CanonicalComplexityPolicy(
            "canonical-math-limits-v1",
            limits.maxExpressionLength(),
            limits.maxAstNodes(),
            limits.maxAstDepth(),
            limits.maxExponentMagnitude(),
            limits.maxNumericDigits(),
            limits.maxFunctionNestingDepth()
        );
    }
}

record CanonicalDisplay(
    String normalizedText,
    String displayLatex,
    List<String> variables,
    int sourceConstraintCount,
    int derivedRestrictionCount,
    boolean reviewRequired
) {
    CanonicalDisplay {
        variables = List.copyOf(variables);
    }
}

record CanonicalProblemDocument(
    String schemaVersion,
    CanonicalSourceParse sourceParse,
    String subjectId,
    String topicId,
    String problemType,
    String taskType,
    List<CanonicalVariable> variables,
    List<CanonicalStatement> statements,
    List<CanonicalRestriction> sourceConstraints,
    List<CanonicalSourceAssumption> sourceAssumptions,
    List<CanonicalRestriction> derivedRestrictions,
    CanonicalComplexityPolicy complexityPolicy,
    CanonicalDisplay display
) {
    CanonicalProblemDocument {
        variables = List.copyOf(variables);
        statements = List.copyOf(statements);
        sourceConstraints = List.copyOf(sourceConstraints);
        sourceAssumptions = List.copyOf(sourceAssumptions);
        derivedRestrictions = List.copyOf(derivedRestrictions);
    }
}

record VerifierInputDocument(
    String schemaVersion,
    String canonicalSchemaVersion,
    String problemType,
    String taskType,
    List<CanonicalVariable> variables,
    List<CanonicalStatement> statements,
    List<CanonicalRestriction> restrictions,
    CanonicalComplexityPolicy complexityPolicy
) {
    VerifierInputDocument {
        variables = List.copyOf(variables);
        statements = List.copyOf(statements);
        restrictions = List.copyOf(restrictions);
    }
}

record CanonicalizationDocuments(
    CanonicalProblemDocument canonicalProblem,
    VerifierInputDocument verifierInput,
    CanonicalDisplay display
) {
}
