package com.verifiedai.verification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.solving.application.canonical.CanonicalMathCodec;
import com.verifiedai.solving.domain.model.canonical.MathExpression;
import com.verifiedai.solving.domain.model.canonical.MathStatement;
import com.verifiedai.verification.domain.model.*;
import java.nio.file.*;
import java.util.*;

public final class NumericTestData {
    private NumericTestData() { }
    public static JsonNode cases() throws java.io.IOException {
        return new ObjectMapper().readTree(Files.readString(Path.of("../../packages/test-fixtures/verification/numeric-v1/cases.json")));
    }
    public static NumericDiagnostic.Request request(JsonNode n) {
        var codec = new CanonicalMathCodec();
        var variables = new HashMap<String, PlanningSource.Domain>();
        n.path("variables").forEach(v -> variables.put(v.path("symbol").textValue(), PlanningSource.Domain.valueOf(v.path("domain").textValue())));
        var restrictions = new ArrayList<PlanningSource.Restriction>();
        n.path("restrictions").forEach(r -> restrictions.add(new PlanningSource.Restriction(r.path("id").textValue(),
            PlanningSource.Relation.valueOf(r.path("relation").textValue()), expression(r.path("left")), expression(r.path("right")))));
        return new NumericDiagnostic.Request(UUID.fromString(n.path("scopeId").textValue()), new MathematicalExecution.Request(
            UUID.fromString(n.path("executionId").textValue()), n.path("correlationId").textValue(), VerificationMethod.NUMERIC_SAMPLING,
            codec.statement(n.path("original")), codec.statement(n.path("candidate")), variables, restrictions));
    }
    private static MathExpression expression(JsonNode n) {
        var codec = new CanonicalMathCodec(); var wrapper = codec.object().put("kind", "EXPRESSION"); wrapper.set("expression", n);
        return ((MathStatement.Expression) codec.statement(wrapper)).expression();
    }
}
