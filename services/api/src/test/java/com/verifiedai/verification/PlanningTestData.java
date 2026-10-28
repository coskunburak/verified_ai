package com.verifiedai.verification;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verifiedai.problem.application.solving.SolveSource;
import com.verifiedai.solving.SolverTestData;
import com.verifiedai.solving.application.canonical.CanonicalMathCodec;
import com.verifiedai.solving.domain.model.canonical.*;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PlanningTestData {
    public static final CanonicalMathCodec CODEC = new CanonicalMathCodec();
    private PlanningTestData() { }
    public static SolveSource source(String type, String task, MathStatement statement) {
        var base = SolverTestData.source(); var root = (ObjectNode) CODEC.parse(base.canonicalJson());
        root.put("problemType", type).put("taskType", task);
        ((ObjectNode) root.path("sourceParse")).put("problemParseSchemaVersion", "problem-parse-v1").put("source", "USER");
        root.putArray("variables");
        if (!type.equals("ARITHMETIC_EXPRESSION")) root.withArray("variables").addObject()
            .put("symbol", "x").put("domain", "REAL").put("role", "VARIABLE");
        var s = CODEC.json(statement);
        if (statement instanceof MathStatement.Expression) s.putNull("left").putNull("right").putNull("relation");
        else s.putNull("expression");
        root.putArray("statements").add(s);
        root.putObject("complexityPolicy").put("version", "canonical-math-limits-v1")
            .put("maxExpressionLength", 512).put("maxAstNodes", 120).put("maxAstDepth", 32)
            .put("maxExponentMagnitude", 12).put("maxNumericDigits", 64).put("maxFunctionNestingDepth", 8);
        return new SolveSource(base.canonicalProblemId(), base.userId(), base.problemSessionId(), base.problemParseId(),
            1, 1, type, task, root.toString());
    }
    public static SolveSource equation() {
        return source("EQUATION", "SOLVE_EQUATION", new MathStatement.Comparison(MathStatement.Relation.EQUALS,
            new MathExpression.Binary(MathExpression.BinaryOperator.ADD, x(), number("1")), number("2")));
    }
    public static SolveSource json(SolveSource s, ObjectNode json) {
        return new SolveSource(s.canonicalProblemId(), s.userId(), s.problemSessionId(), s.problemParseId(),
            s.problemParseRevision(), s.canonicalRevision(), s.problemType(), s.taskType(), json.toString());
    }
    public static CanonicalSolution solution(SolveSource source, MathStatement answer) {
        return solution(source, answer, Role.PRIMARY, null);
    }
    public static CanonicalSolution solution(SolveSource s, MathStatement answer, Role role, UUID parent) {
        var origin = new Origin(role, UUID.randomUUID()); var id = CanonicalSolution.identity(origin, Version.CURRENT);
        var root = CODEC.parse(s.canonicalJson());
        return new CanonicalSolution(id, new Lineage(s.userId(), s.problemSessionId(), s.canonicalProblemId(),
            s.problemParseId(), s.problemParseRevision(), s.canonicalRevision(), origin, parent), Version.CURRENT,
            new Provenance(UUID.randomUUID(), null, "LOCAL_FIXTURE", "fixture", null, null, null, "solution-candidate-v1", 1),
            "LOCAL_FIXTURE", s.problemType(), s.taskType(), "ISOLATE_VARIABLE", answer,
            List.of(new Step(CanonicalSolution.stepIdentity(id, "s1"), "s1", 1, Category.CONCLUDE, answer, "Synthetic claim", List.of())),
            ids(root, "sourceAssumptions"), ids(root, "sourceConstraints"), ids(root, "derivedRestrictions"),
            List.of(), List.of(), Instant.parse("2026-10-28T09:00:00Z"));
    }
    private static List<String> ids(com.fasterxml.jackson.databind.JsonNode root, String field) {
        var result = new java.util.ArrayList<String>(); root.path(field).forEach(n -> result.add(n.path("id").asText())); return result;
    }
    public static CanonicalSolution answer(SolveSource source) {
        return solution(source, new MathStatement.Comparison(MathStatement.Relation.EQUALS, x(), number("1")));
    }
    public static MathExpression.Number number(String n) { return new MathExpression.Number(n.contains(".") ? MathExpression.NumericType.DECIMAL : MathExpression.NumericType.INTEGER, n); }
    public static MathExpression.Variable x() { return new MathExpression.Variable("x"); }
}
