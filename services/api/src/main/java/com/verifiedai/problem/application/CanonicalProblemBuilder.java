package com.verifiedai.problem.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.problem.domain.model.ProblemParseSupportStatus;
import com.verifiedai.problem.infrastructure.persistence.ProblemParseJpaEntity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
final class CanonicalProblemBuilder {
    static final String CANONICAL_SCHEMA_VERSION = "canonical-problem-v1";
    static final String VERIFIER_SCHEMA_VERSION = "verifier-input-v1";

    private final ObjectMapper objectMapper = new ObjectMapper();

    CanonicalProblemBuilder() {
    }

    CanonicalizationDocuments build(ProblemParseJpaEntity parse) {
        JsonNode root = parseNormalizedProblem(parse);
        requireSupportedParse(parse, root);

        Set<String> declaredSymbols = declaredSymbols(root);
        List<CanonicalVariable> variables = variables(root);
        CanonicalMathParser parser = new CanonicalMathParser(declaredSymbols, CanonicalMathLimits.V1);
        CanonicalComplexityPolicy complexityPolicy = CanonicalComplexityPolicy.from(CanonicalMathLimits.V1);

        List<CanonicalStatement> statements = new ArrayList<>();
        List<CanonicalRestriction> derivedRestrictions = new ArrayList<>();
        for (JsonNode expression : root.path("expressions")) {
            String expressionId = requiredText(expression, "id");
            String normalizedText = requiredText(expression, "normalizedText");
            List<String> sourceBlockIds = sourceBlockIds(expression);
            ParsedStatement parsed = parseStatement(parser, expressionId, normalizedText, textOrNull(expression, "relation"), sourceBlockIds);
            statements.add(parsed.statement());
            addDerivedRestrictions(derivedRestrictions, expressionId, sourceBlockIds, parsed.derivedRestrictions());
        }
        if (statements.isEmpty()) {
            throw new CanonicalizationException(
                CanonicalizationFailure.UNSUPPORTED_EXPRESSION,
                "Canonical v1 requires at least one expression"
            );
        }

        ParsedConstraints parsedConstraints = sourceConstraints(parser, root.path("constraints"));
        List<CanonicalRestriction> sourceConstraints = parsedConstraints.sourceConstraints();
        derivedRestrictions.addAll(parsedConstraints.derivedRestrictions());
        List<CanonicalSourceAssumption> sourceAssumptions = sourceAssumptions(root.path("assumptions"));

        CanonicalDisplay display = display(root, variables, sourceConstraints.size(), derivedRestrictions.size());
        CanonicalProblemDocument canonicalProblem = new CanonicalProblemDocument(
            CANONICAL_SCHEMA_VERSION,
            new CanonicalSourceParse(parse.id(), parse.revision(), parse.schemaVersion(), parse.source()),
            textOrNull(root, "subjectId"),
            textOrNull(root, "topicId"),
            textOrNull(root, "problemType"),
            textOrNull(root, "taskType"),
            variables,
            statements,
            sourceConstraints,
            sourceAssumptions,
            derivedRestrictions,
            complexityPolicy,
            display
        );
        List<CanonicalRestriction> verifierRestrictions = new ArrayList<>(sourceConstraints);
        verifierRestrictions.addAll(derivedRestrictions);
        VerifierInputDocument verifierInput = new VerifierInputDocument(
            VERIFIER_SCHEMA_VERSION,
            CANONICAL_SCHEMA_VERSION,
            canonicalProblem.problemType(),
            canonicalProblem.taskType(),
            variables,
            statements,
            verifierRestrictions,
            complexityPolicy
        );
        return new CanonicalizationDocuments(canonicalProblem, verifierInput, display);
    }

    private JsonNode parseNormalizedProblem(ProblemParseJpaEntity parse) {
        try {
            return objectMapper.readTree(parse.normalizedProblemJson());
        } catch (Exception exception) {
            throw new CanonicalizationException(
                CanonicalizationFailure.UNSUPPORTED_PARSE,
                "Stored ProblemParse JSON cannot be read"
            );
        }
    }

    private void requireSupportedParse(ProblemParseJpaEntity parse, JsonNode root) {
        if (!ProblemParseSupportStatus.SUPPORTED.name().equals(parse.supportStatus())) {
            throw new CanonicalizationException(
                CanonicalizationFailure.UNSUPPORTED_PARSE,
                "Only supported parser revisions can be canonicalized"
            );
        }
        if (!"problem-parse-v1".equals(textOrNull(root, "schemaVersion"))) {
            throw new CanonicalizationException(
                CanonicalizationFailure.UNSUPPORTED_PARSE,
                "ProblemParse schema version is not supported"
            );
        }
        String problemType = textOrNull(root, "problemType");
        String taskType = textOrNull(root, "taskType");
        if (!Set.of("ARITHMETIC_EXPRESSION", "ALGEBRAIC_EXPRESSION", "EQUATION", "INEQUALITY").contains(problemType)
            || !Set.of("EVALUATE", "SIMPLIFY", "SOLVE_EQUATION", "SOLVE_INEQUALITY").contains(taskType)) {
            throw new CanonicalizationException(
                CanonicalizationFailure.UNSUPPORTED_PARSE,
                "Problem type or task type is outside canonical v1"
            );
        }
    }

    private Set<String> declaredSymbols(JsonNode root) {
        Set<String> symbols = new LinkedHashSet<>();
        for (JsonNode variable : root.path("variables")) {
            String symbol = requiredText(variable, "symbol");
            if (!symbol.matches("[A-Za-z][A-Za-z0-9_]{0,15}") || !symbols.add(symbol)) {
                throw new CanonicalizationException(
                    CanonicalizationFailure.UNSAFE_IDENTIFIER,
                    "Variable declaration is unsafe or duplicated"
                );
            }
        }
        return symbols;
    }

    private List<CanonicalVariable> variables(JsonNode root) {
        List<CanonicalVariable> variables = new ArrayList<>();
        for (JsonNode variable : root.path("variables")) {
            variables.add(new CanonicalVariable(
                requiredText(variable, "symbol"),
                requiredText(variable, "role"),
                "UNKNOWN",
                sourceBlockIds(variable)
            ));
        }
        return List.copyOf(variables);
    }

    private ParsedStatement parseStatement(
        CanonicalMathParser parser,
        String expressionId,
        String normalizedText,
        String relationHint,
        List<String> sourceBlockIds
    ) {
        RelationSplit relation = splitRelation(normalizedText);
        if (relationHint != null && relation == null) {
            throw new CanonicalizationException(
                CanonicalizationFailure.UNSUPPORTED_EXPRESSION,
                "Parser relation hint has no matching expression relation"
            );
        }
        if (relationHint != null && !relationHint.equals(relation.relation())) {
            throw new CanonicalizationException(
                CanonicalizationFailure.UNSUPPORTED_EXPRESSION,
                "Parser relation hint does not match expression text"
            );
        }
        if (relation == null) {
            CanonicalMathParser.ParsedExpression expression = parser.parse(normalizedText);
            return new ParsedStatement(
                CanonicalStatement.expression(expressionId, expression.node(), sourceBlockIds),
                expression.derivedRestrictions()
            );
        }
        CanonicalMathParser.ParsedExpression left = parser.parse(relation.left());
        CanonicalMathParser.ParsedExpression right = parser.parse(relation.right());
        List<CanonicalRestriction> derived = new ArrayList<>();
        derived.addAll(left.derivedRestrictions());
        derived.addAll(right.derivedRestrictions());
        return new ParsedStatement(
            CanonicalStatement.relation(expressionId, relation.relation(), left.node(), right.node(), sourceBlockIds),
            derived
        );
    }

    private ParsedConstraints sourceConstraints(CanonicalMathParser parser, JsonNode constraints) {
        List<CanonicalRestriction> result = new ArrayList<>();
        List<CanonicalRestriction> derivedRestrictions = new ArrayList<>();
        for (JsonNode constraint : constraints) {
            if (!constraint.path("explicit").asBoolean(false)) {
                throw new CanonicalizationException(
                    CanonicalizationFailure.INVALID_CONSTRAINT,
                    "Canonical constraints must be explicit"
                );
            }
            String normalizedText = requiredText(constraint, "normalizedText");
            RelationSplit relation = splitRelation(normalizedText);
            if (relation == null) {
                throw new CanonicalizationException(
                    CanonicalizationFailure.INVALID_CONSTRAINT,
                    "Canonical v1 supports only relational constraints"
                );
            }
            CanonicalMathParser.ParsedExpression left = parser.parse(relation.left());
            CanonicalMathParser.ParsedExpression right = parser.parse(relation.right());
            String constraintId = requiredText(constraint, "id");
            List<String> sourceBlockIds = sourceBlockIds(constraint);
            result.add(new CanonicalRestriction(
                constraintId,
                relation.relation(),
                left.node(),
                right.node(),
                "SOURCE_EXPLICIT",
                "SOURCE",
                sourceBlockIds
            ));
            addDerivedRestrictions(derivedRestrictions, constraintId, sourceBlockIds, left.derivedRestrictions());
            addDerivedRestrictions(derivedRestrictions, constraintId, sourceBlockIds, right.derivedRestrictions());
        }
        return new ParsedConstraints(result, derivedRestrictions);
    }

    private List<CanonicalSourceAssumption> sourceAssumptions(JsonNode assumptions) {
        List<CanonicalSourceAssumption> result = new ArrayList<>();
        for (JsonNode assumption : assumptions) {
            if (!assumption.path("explicit").asBoolean(false)) {
                throw new CanonicalizationException(
                    CanonicalizationFailure.INVALID_CONSTRAINT,
                    "Canonical assumptions must be explicit"
                );
            }
            result.add(new CanonicalSourceAssumption(
                requiredText(assumption, "id"),
                requiredText(assumption, "text"),
                "SOURCE",
                sourceBlockIds(assumption)
            ));
        }
        return List.copyOf(result);
    }

    private void addDerivedRestrictions(
        List<CanonicalRestriction> target,
        String sourceExpressionId,
        List<String> sourceBlockIds,
        List<CanonicalRestriction> restrictions
    ) {
        for (CanonicalRestriction restriction : restrictions) {
            target.add(new CanonicalRestriction(
                "derived-" + (target.size() + 1) + "-" + sourceExpressionId,
                restriction.relation(),
                restriction.left(),
                restriction.right(),
                restriction.reason(),
                restriction.provenance(),
                sourceBlockIds
            ));
        }
    }

    private CanonicalDisplay display(
        JsonNode root,
        List<CanonicalVariable> variables,
        int sourceConstraintCount,
        int derivedRestrictionCount
    ) {
        JsonNode firstExpression = root.path("expressions").isArray() && root.path("expressions").size() > 0
            ? root.path("expressions").get(0)
            : null;
        return new CanonicalDisplay(
            firstExpression == null ? null : textOrNull(firstExpression, "normalizedText"),
            firstExpression == null ? null : textOrNull(firstExpression, "displayLatex"),
            variables.stream().map(CanonicalVariable::symbol).toList(),
            sourceConstraintCount,
            derivedRestrictionCount,
            root.path("reviewRequired").asBoolean(false)
        );
    }

    private RelationSplit splitRelation(String text) {
        int depth = 0;
        for (int index = 0; index < text.length(); index += 1) {
            char current = text.charAt(index);
            if (current == '(') {
                depth += 1;
                continue;
            }
            if (current == ')') {
                depth = Math.max(0, depth - 1);
                continue;
            }
            if (depth != 0) {
                continue;
            }
            if (index + 1 < text.length()) {
                String two = text.substring(index, index + 2);
                String relation = switch (two) {
                    case "<=" -> "LESS_THAN_OR_EQUAL";
                    case ">=" -> "GREATER_THAN_OR_EQUAL";
                    case "!=" -> "NOT_EQUALS";
                    default -> null;
                };
                if (relation != null) {
                    return relation(text, index, 2, relation);
                }
            }
            String relation = switch (current) {
                case '=' -> "EQUALS";
                case '<' -> "LESS_THAN";
                case '>' -> "GREATER_THAN";
                default -> null;
            };
            if (relation != null) {
                return relation(text, index, 1, relation);
            }
        }
        return null;
    }

    private RelationSplit relation(String text, int index, int operatorLength, String relation) {
        String left = text.substring(0, index).strip();
        String right = text.substring(index + operatorLength).strip();
        if (left.isBlank() || right.isBlank()) {
            throw new CanonicalizationException(
                CanonicalizationFailure.UNSUPPORTED_EXPRESSION,
                "Relation side is blank"
            );
        }
        return new RelationSplit(left, relation, right);
    }

    private String requiredText(JsonNode node, String field) {
        String value = textOrNull(node, field);
        if (value == null || value.isBlank()) {
            throw new CanonicalizationException(
                CanonicalizationFailure.UNSUPPORTED_PARSE,
                "Required ProblemParse field is missing"
            );
        }
        return value;
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private List<String> sourceBlockIds(JsonNode node) {
        List<String> values = new ArrayList<>();
        for (JsonNode blockId : node.path("sourceBlockIds")) {
            values.add(blockId.asText());
        }
        return List.copyOf(values);
    }

    private record ParsedStatement(
        CanonicalStatement statement,
        List<CanonicalRestriction> derivedRestrictions
    ) {
    }

    private record ParsedConstraints(
        List<CanonicalRestriction> sourceConstraints,
        List<CanonicalRestriction> derivedRestrictions
    ) {
        private ParsedConstraints {
            sourceConstraints = List.copyOf(sourceConstraints);
            derivedRestrictions = List.copyOf(derivedRestrictions);
        }
    }

    private record RelationSplit(String left, String relation, String right) {
    }
}
