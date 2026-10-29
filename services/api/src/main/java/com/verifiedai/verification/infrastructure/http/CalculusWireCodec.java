package com.verifiedai.verification.infrastructure.http;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verifiedai.solving.application.canonical.CanonicalMathCodec;
import com.verifiedai.solving.domain.model.canonical.MathExpression;
import com.verifiedai.solving.domain.model.canonical.MathStatement;
import com.verifiedai.verification.domain.model.CalculusExecution;
import com.verifiedai.verification.domain.model.CalculusExecution.*;
import com.verifiedai.verification.domain.model.MathematicalExecution.ExactValue;
import com.verifiedai.verification.domain.model.MathematicalExecution.Outcome;
import com.verifiedai.verification.domain.model.PlanningSource;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Explicit internal wire boundary. Intentionally not a Spring bean or production executor. */
public final class CalculusWireCodec {
    private final CanonicalMathCodec canonical = new CanonicalMathCodec();
    private final ObjectMapper mapper = new ObjectMapper(JsonFactory.builder().streamReadConstraints(
        StreamReadConstraints.builder().maxNestingDepth(80).maxStringLength(4096).build()).build())
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public byte[] request(Request request) {
        var n = canonical.object();
        n.put("schemaVersion", CalculusExecution.REQUEST_VERSION).put("executionId", request.executionId().toString())
            .put("correlationId", request.correlationId()).put("method", request.method().name());
        // UUID serializers can emit native binary values into Jackson's TokenBuffer.
        // Write the published textual UUID contract explicitly instead of valueToTree.
        var scope = request.scope();
        n.putObject("scope").put("identity", scope.identity().toString())
            .put("sourceRevision", scope.sourceRevision()).put("candidateRevision", scope.candidateRevision())
            .put("sourceVersion", scope.sourceVersion()).put("candidateVersion", scope.candidateVersion());
        n.putObject("variable").put("symbol", request.variable()).put("domain", request.domain().name());
        var interval = n.putObject("interval").put("kind", "OPEN_INTERVAL");
        interval.set("lower", exactJson(request.interval().lower()));
        interval.set("upper", exactJson(request.interval().upper()));
        n.set("original", canonical.json(request.original())); n.set("candidate", canonical.json(request.candidate()));
        var restrictions = n.putArray("restrictions");
        for (var r : request.restrictions()) {
            var item = restrictions.addObject().put("id", r.id()).put("relation", r.relation().name());
            item.set("left", canonical.json(r.left())); item.set("right", canonical.json(r.right()));
        }
        if (request.approach() == null) n.putNull("approach");
        else {
            var approach = n.putObject("approach").put("direction", request.approach().direction().name());
            approach.set("target", exactJson(request.approach().target()));
        }
        if (request.integralScope() == null) n.putNull("integralScope");
        else n.put("integralScope", request.integralScope().name());
        var bytes = n.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 65536) throw new IllegalArgumentException("Calculus request too large");
        return bytes;
    }
    public Request request(byte[] bytes) {
        var n = parse(bytes, 65536);
        fields(n, "schemaVersion", "executionId", "correlationId", "scope", "method", "variable", "interval", "original", "candidate", "restrictions", "approach", "integralScope");
        if (!CalculusExecution.REQUEST_VERSION.equals(text(n, "schemaVersion"))) throw new IllegalArgumentException("Unsupported calculus schema");
        var v = n.path("variable"); fields(v, "symbol", "domain");
        var i = n.path("interval"); fields(i, "kind", "lower", "upper");
        if (!"OPEN_INTERVAL".equals(text(i, "kind"))) throw new IllegalArgumentException("Unsupported calculus interval");
        var restrictions = new ArrayList<PlanningSource.Restriction>();
        if (!n.path("restrictions").isArray() || n.path("restrictions").size() > 48) throw new IllegalArgumentException("Invalid restrictions");
        for (var r : n.path("restrictions")) {
            fields(r, "id", "relation", "left", "right");
            restrictions.add(new PlanningSource.Restriction(text(r, "id"), PlanningSource.Relation.valueOf(text(r, "relation")), expression(r.path("left")), expression(r.path("right"))));
        }
        Approach approach = null;
        if (!n.path("approach").isNull()) {
            var a = n.path("approach"); fields(a, "target", "direction");
            approach = new Approach(exact(a.path("target")), Direction.valueOf(text(a, "direction")));
        }
        return new Request(UUID.fromString(text(n, "executionId")), text(n, "correlationId"), scope(n.path("scope")),
            Method.valueOf(text(n, "method")), text(v, "symbol"), PlanningSource.Domain.valueOf(text(v, "domain")),
            new Interval(exact(i.path("lower")), exact(i.path("upper"))), expression(n.path("original")), expression(n.path("candidate")),
            restrictions, approach, n.path("integralScope").isNull() ? null : IntegralScope.valueOf(text(n, "integralScope")));
    }
    public Result result(byte[] bytes, Request request) {
        var n = parse(bytes, 16384);
        fields(n, "schemaVersion", "executionId", "correlationId", "scope", "method", "methodVersion", "verifierVersion", "outcome", "reasonCode", "restrictionIds", "proof", "diagnostic");
        if (!CalculusExecution.RESPONSE_VERSION.equals(text(n, "schemaVersion"))) throw new IllegalArgumentException("Unsupported calculus result schema");
        Certificate proof = null;
        if (!n.path("proof").isNull()) {
            var p = n.path("proof"); fields(p, "descriptor", "scope", "leftValue", "rightValue");
            proof = new Certificate(Descriptor.valueOf(text(p, "descriptor")), ProofScope.valueOf(text(p, "scope")), exact(p.path("leftValue")), exact(p.path("rightValue")));
        }
        Diagnostic diagnostic = null;
        if (!n.path("diagnostic").isNull()) {
            var d = n.path("diagnostic"); fields(d, "policy", "attempted", "evaluated", "agreeing");
            diagnostic = new Diagnostic(text(d, "policy"), integer(d, "attempted"), integer(d, "evaluated"), integer(d, "agreeing"));
        }
        var result = new Result(UUID.fromString(text(n, "executionId")), text(n, "correlationId"), scope(n.path("scope")), Method.valueOf(text(n, "method")),
            text(n, "methodVersion"), text(n, "verifierVersion"), Outcome.valueOf(text(n, "outcome")), Reason.valueOf(text(n, "reasonCode")),
            canonical.strings(n.path("restrictionIds")), proof, diagnostic);
        result.validateFor(request); return result;
    }
    private Scope scope(JsonNode n) {
        fields(n, "identity", "sourceRevision", "candidateRevision", "sourceVersion", "candidateVersion");
        return new Scope(UUID.fromString(text(n, "identity")), integer(n, "sourceRevision"), integer(n, "candidateRevision"), text(n, "sourceVersion"), text(n, "candidateVersion"));
    }
    private JsonNode exactJson(ExactValue value) {
        return value == null ? mapper.getNodeFactory().nullNode() : canonical.object()
            .put("numerator", value.numerator()).put("denominator", value.denominator());
    }
    private MathExpression expression(JsonNode n) {
        ast(n, 1, new int[]{0});
        ObjectNode wrapper = canonical.object().put("kind", "EXPRESSION"); wrapper.set("expression", n);
        return ((MathStatement.Expression) canonical.statement(wrapper)).expression();
    }
    private void ast(JsonNode n, int depth, int[] count) {
        if (depth > 32 || ++count[0] > 120) throw new IllegalArgumentException("Calculus AST limit");
        switch (text(n, "kind")) {
            case "NUMBER" -> { fields(n, "kind", "numericType", "value"); text(n, "numericType"); text(n, "value"); }
            case "VARIABLE" -> { fields(n, "kind", "symbol"); text(n, "symbol"); }
            case "UNARY" -> { fields(n, "kind", "operator", "operand"); text(n, "operator"); ast(n.path("operand"), depth+1, count); }
            case "BINARY" -> { fields(n, "kind", "operator", "left", "right"); text(n, "operator"); ast(n.path("left"), depth+1, count); ast(n.path("right"), depth+1, count); }
            case "FUNCTION" -> {
                fields(n, "kind", "function", "args"); text(n, "function");
                if (!n.path("args").isArray() || n.path("args").size() != 1) throw new IllegalArgumentException("Invalid function arity");
                ast(n.path("args").get(0), depth+1, count);
            }
            default -> throw new IllegalArgumentException("Unsupported calculus AST");
        }
    }
    private JsonNode parse(byte[] bytes, int limit) {
        if (bytes.length > limit) throw new IllegalArgumentException("Calculus wire size limit");
        try { return mapper.readTree(bytes); }
        catch (java.io.IOException e) { throw new IllegalArgumentException("Malformed calculus JSON"); }
    }
    private static ExactValue exact(JsonNode n) {
        if (n.isNull()) return null;
        fields(n, "numerator", "denominator"); return new ExactValue(text(n, "numerator"), text(n, "denominator"));
    }
    private static void fields(JsonNode n, String... expected) {
        if (n == null || !n.isObject()) throw new IllegalArgumentException("Expected calculus object");
        Set<String> actual = new HashSet<>(); n.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(Set.of(expected))) throw new IllegalArgumentException("Unexpected calculus fields");
    }
    private static String text(JsonNode n, String key) {
        if (!n.path(key).isTextual()) throw new IllegalArgumentException("Expected calculus text");
        return n.path(key).textValue();
    }
    private static int integer(JsonNode n, String key) {
        if (!n.path(key).isIntegralNumber() || !n.path(key).canConvertToInt()) throw new IllegalArgumentException("Expected calculus integer");
        return n.path(key).intValue();
    }
}
