package com.verifiedai.verification.infrastructure.http;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.*;
import com.verifiedai.solving.application.canonical.CanonicalMathCodec;
import com.verifiedai.verification.domain.model.MathematicalExecution;
import com.verifiedai.verification.domain.model.MathematicalExecution.*;
import com.verifiedai.verification.domain.model.VerificationMethod;
import java.util.*;

/** Explicit allowlisted wire representation; never serializes source ownership or presentation fields. */
public final class ExecutionWireCodec {
    private final CanonicalMathCodec canonical = new CanonicalMathCodec();
    private final ObjectMapper mapper = new ObjectMapper(JsonFactory.builder().streamReadConstraints(
        StreamReadConstraints.builder().maxNestingDepth(48).maxStringLength(4096).build()).build())
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public byte[] request(Request request) {
        var n = canonical.object();
        n.put("schemaVersion", MathematicalExecution.REQUEST_VERSION).put("executionId", request.executionId().toString())
            .put("correlationId", request.correlationId()).put("method", request.method().name());
        n.set("original", canonical.json(request.original())); n.set("candidate", canonical.json(request.candidate()));
        var variables = n.putArray("variables");
        request.variables().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(v ->
            variables.addObject().put("symbol", v.getKey()).put("domain", v.getValue().name()));
        var restrictions = n.putArray("restrictions");
        for (var r : request.restrictions()) {
            var item = restrictions.addObject().put("id", r.id()).put("relation", r.relation().name());
            item.set("left", canonical.json(r.left())); item.set("right", canonical.json(r.right()));
        }
        var bytes = n.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > 65536) throw new IllegalArgumentException("Request limit exceeded");
        return bytes;
    }
    public Result result(byte[] bytes, Request request) {
        if (bytes.length > 16384) throw new IllegalArgumentException("Response limit exceeded");
        try {
            var n = mapper.readTree(bytes);
            fields(n, Set.of("schemaVersion", "executionId", "correlationId", "method", "methodVersion", "verifierVersion",
                "outcome", "reasonCode", "restrictionIds", "proof"));
            if (!MathematicalExecution.RESPONSE_VERSION.equals(text(n, "schemaVersion"))
                || !request.executionId().toString().equals(text(n, "executionId"))
                || !request.correlationId().equals(text(n, "correlationId"))) throw new IllegalArgumentException("Response identity mismatch");
            var refs = n.path("restrictionIds");
            if (!refs.isArray() || refs.size() > 48) throw new IllegalArgumentException("Invalid restriction references");
            var ids = new ArrayList<String>();
            for (var ref : refs) {
                if (!ref.isTextual() || ref.textValue().length() > 96) throw new IllegalArgumentException("Invalid restriction reference");
                ids.add(ref.textValue());
            }
            Proof proof = null;
            var p = n.path("proof");
            if (!p.isNull()) {
                fields(p, Set.of("descriptor", "leftValue", "rightValue", "distinctRootCount"));
                var count = p.path("distinctRootCount");
                if (!count.isNull() && (!count.isIntegralNumber() || !count.canConvertToInt())) throw new IllegalArgumentException("Invalid root count");
                proof = new Proof(Descriptor.valueOf(text(p, "descriptor")), exact(p.path("leftValue")), exact(p.path("rightValue")),
                    count.isNull() ? null : count.intValue());
            }
            var result = new Result(VerificationMethod.valueOf(text(n, "method")), text(n, "methodVersion"), text(n, "verifierVersion"),
                Outcome.valueOf(text(n, "outcome")), Reason.valueOf(text(n, "reasonCode")), ids, proof);
            result.validateFor(request); return result;
        } catch (java.io.IOException e) { throw new IllegalArgumentException("Malformed verifier response"); }
    }
    private static ExactValue exact(JsonNode n) {
        if (n.isNull()) return null;
        fields(n, Set.of("numerator", "denominator"));
        return new ExactValue(text(n, "numerator"), text(n, "denominator"));
    }
    private static void fields(JsonNode n, Set<String> expected) {
        if (n == null || !n.isObject()) throw new IllegalArgumentException("Expected object");
        Set<String> actual = new HashSet<>(); n.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) throw new IllegalArgumentException("Unexpected response fields");
    }
    private static String text(JsonNode n, String key) {
        var value = n.path(key);
        if (!value.isTextual()) throw new IllegalArgumentException("Expected text");
        return value.textValue();
    }
}
