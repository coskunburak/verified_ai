package com.verifiedai.verification.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.verifiedai.problem.application.solving.SolveSource;
import com.verifiedai.solving.application.canonical.CanonicalMathCodec;
import com.verifiedai.solving.domain.model.canonical.MathExpression;
import com.verifiedai.solving.domain.model.canonical.MathStatement;
import com.verifiedai.verification.domain.model.PlanningSource;
import com.verifiedai.verification.domain.model.PlanningSource.*;
import com.verifiedai.verification.domain.model.VerificationPlan.Reason;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Decodes existing canonical syntax only. No normalization, parsing of math text, or verifier call. */
@Component
public final class VerificationSourceReader {
    private final CanonicalMathCodec codec;
    public VerificationSourceReader(CanonicalMathCodec codec) { this.codec = codec; }

    public PlanningSource read(SolveSource source) {
        try {
            var root = codec.parse(source.canonicalJson());
            if (!"canonical-problem-v1".equals(text(root, "schemaVersion"))) reject(Reason.UNSUPPORTED_REPRESENTATION);
            var parse = root.path("sourceParse");
            if (!source.problemParseId().toString().equals(text(parse, "problemParseId"))
                || integer(parse, "problemParseRevision") != source.problemParseRevision()
                || !source.problemType().equals(text(root, "problemType"))
                || !source.taskType().equals(text(root, "taskType"))) reject(Reason.LINEAGE_MISMATCH);
            if (!"problem-parse-v1".equals(text(parse, "problemParseSchemaVersion"))) reject(Reason.UNSUPPORTED_REPRESENTATION);
            require(Set.of("AI", "USER").contains(text(parse, "source")));
            ProblemType type;
            TaskType task;
            try { type = ProblemType.valueOf(source.problemType()); }
            catch (IllegalArgumentException e) { throw new SourceRejected(Reason.UNSUPPORTED_PROBLEM_TYPE); }
            try { task = TaskType.valueOf(source.taskType()); }
            catch (IllegalArgumentException e) { throw new SourceRejected(Reason.UNSUPPORTED_TASK_TYPE); }
            var limits = root.path("complexityPolicy");
            if (!"canonical-math-limits-v1".equals(text(limits, "version"))) reject(Reason.UNSUPPORTED_REPRESENTATION);
            for (var limit : new Object[][]{{"maxExpressionLength",512}, {"maxAstNodes",120},
                {"maxAstDepth",32}, {"maxExponentMagnitude",12}, {"maxNumericDigits",64}, {"maxFunctionNestingDepth",8}}) {
                if (integer(limits, (String) limit[0]) != (Integer) limit[1]) reject(Reason.UNSUPPORTED_REPRESENTATION);
            }
            var variables = new LinkedHashMap<String, Domain>();
            for (var v : array(root, "variables", 16)) {
                String symbol = text(v, "symbol"); new MathExpression.Variable(symbol);
                require(Set.of("VARIABLE", "PARAMETER", "UNKNOWN").contains(text(v, "role")));
                require(variables.putIfAbsent(symbol, Domain.valueOf(text(v, "domain"))) == null);
            }
            var statements = new ArrayList<MathStatement>();
            for (var s : array(root, "statements", 8)) statements.add(statement(s));
            require(!statements.isEmpty());
            var restrictions = new ArrayList<Restriction>();
            var constraintIds = restrictions(root, "sourceConstraints", 16, "SOURCE", restrictions);
            var derivedIds = restrictions(root, "derivedRestrictions", 32, "DERIVED", restrictions);
            var assumptionIds = new ArrayList<String>();
            for (var a : array(root, "sourceAssumptions", 16)) {
                require("SOURCE".equals(text(a, "provenance")));
                require(a.path("text").isTextual() && a.path("text").textValue().length() <= 1000);
                assumptionIds.add(id(a));
            }
            var allIds = new ArrayList<>(constraintIds); allIds.addAll(derivedIds); allIds.addAll(assumptionIds);
            require(new HashSet<>(allIds).size() == allIds.size());
            require(root.path("display").path("reviewRequired").isBoolean());
            return new PlanningSource(new Identity(source.userId(), source.problemSessionId(), source.canonicalProblemId(),
                source.problemParseId(), source.problemParseRevision(), source.canonicalRevision()), type, task,
                statements, variables, restrictions, assumptionIds, constraintIds, derivedIds,
                root.path("display").path("reviewRequired").booleanValue());
        } catch (SourceRejected e) { throw e; }
        catch (IllegalArgumentException | NullPointerException e) { throw new SourceRejected(Reason.INVALID_CANONICAL_STRUCTURE); }
    }
    private List<String> restrictions(JsonNode root, String name, int max, String provenance, List<Restriction> result) {
        var ids = new ArrayList<String>();
        for (var r : array(root, name, max)) {
            require(provenance.equals(text(r, "provenance")));
            require(Set.of("SOURCE_EXPLICIT", "DENOMINATOR_NON_ZERO", "SQRT_DOMAIN_NON_NEGATIVE",
                "LOG_DOMAIN_POSITIVE", "TAN_DOMAIN_COS_NON_ZERO").contains(text(r, "reason")));
            String id = id(r); ids.add(id);
            result.add(new Restriction(id, Relation.valueOf(text(r, "relation")),
                expression(r.path("left")), expression(r.path("right"))));
        }
        return List.copyOf(ids);
    }
    private MathStatement statement(JsonNode n) {
        return switch (text(n, "kind")) {
            case "EXPRESSION" -> {
                require(n.path("left").isNull() && n.path("right").isNull() && n.path("relation").isNull());
                yield new MathStatement.Expression(expression(n.path("expression")));
            }
            case "RELATION" -> {
                require(n.path("expression").isNull());
                MathStatement.Relation relation;
                try { relation = MathStatement.Relation.valueOf(text(n, "relation")); }
                catch (IllegalArgumentException e) { throw new SourceRejected(Reason.UNSUPPORTED_STATEMENT); }
                yield new MathStatement.Comparison(relation, expression(n.path("left")), expression(n.path("right")));
            }
            default -> throw new SourceRejected(Reason.UNSUPPORTED_STATEMENT);
        };
    }
    private MathExpression expression(JsonNode n) {
        validateExpression(n, 1, new int[]{0});
        var wrapper = codec.object().put("kind", "EXPRESSION"); wrapper.set("expression", n);
        return ((MathStatement.Expression) codec.statement(wrapper)).expression();
    }
    private void validateExpression(JsonNode n, int depth, int[] nodes) {
        require(depth <= 32 && ++nodes[0] <= 120 && n.isObject());
        switch (text(n, "kind")) {
            case "NUMBER" -> { text(n, "value"); text(n, "numericType"); }
            case "VARIABLE" -> text(n, "symbol");
            case "UNARY" -> { text(n, "operator"); validateExpression(n.path("operand"), depth + 1, nodes); }
            case "BINARY" -> {
                text(n, "operator"); validateExpression(n.path("left"), depth + 1, nodes);
                validateExpression(n.path("right"), depth + 1, nodes);
            }
            case "FUNCTION" -> {
                try { MathExpression.FunctionName.valueOf(text(n, "function")); }
                catch (IllegalArgumentException e) { throw new SourceRejected(Reason.UNKNOWN_FUNCTION); }
                var args = array(n, "args", 1); require(args.size() == 1);
                validateExpression(args.get(0), depth + 1, nodes);
            }
            default -> throw new SourceRejected(Reason.INVALID_CANONICAL_STRUCTURE);
        }
    }
    private static JsonNode array(JsonNode n, String field, int max) {
        var value = n.path(field); require(value.isArray() && value.size() <= max); return value;
    }
    private static String id(JsonNode n) { String id = text(n, "id"); require(id.length() <= 96); return id; }
    private static String text(JsonNode n, String field) {
        var value = n.path(field); require(value.isTextual() && !value.textValue().isBlank()); return value.textValue();
    }
    private static int integer(JsonNode n, String field) {
        var value = n.path(field); require(value.isIntegralNumber() && value.canConvertToInt()); return value.intValue();
    }
    private static void require(boolean condition) { if (!condition) reject(Reason.INVALID_CANONICAL_STRUCTURE); }
    private static void reject(Reason reason) { throw new SourceRejected(reason); }
    public static final class SourceRejected extends RuntimeException {
        private final Reason reason;
        SourceRejected(Reason reason) { super(reason.name()); this.reason = reason; }
        public Reason reason() { return reason; }
    }
}
