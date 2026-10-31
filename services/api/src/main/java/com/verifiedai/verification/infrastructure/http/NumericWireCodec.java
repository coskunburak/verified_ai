package com.verifiedai.verification.infrastructure.http;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verifiedai.verification.domain.model.NumericDiagnostic;
import java.util.*;

/** Strict non-proof wire codec; identifiers bind diagnostics, never confer ownership. */
public final class NumericWireCodec {
    private final ObjectMapper mapper = new ObjectMapper(JsonFactory.builder().streamReadConstraints(
        StreamReadConstraints.builder().maxNestingDepth(48).maxStringLength(4096).build()).build())
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public byte[] request(NumericDiagnostic.Request request) {
        try {
            var node = (ObjectNode) mapper.readTree(new ExecutionWireCodec().request(request.input()));
            node.put("schemaVersion", NumericDiagnostic.REQUEST_VERSION).put("scopeId", request.scopeId().toString())
                .put("policyVersion", NumericDiagnostic.POLICY);
            byte[] bytes = mapper.writeValueAsBytes(node);
            if (bytes.length > 65536) throw new IllegalArgumentException("Diagnostic request too large");
            return bytes;
        } catch (java.io.IOException e) { throw new IllegalArgumentException("Cannot encode diagnostic"); }
    }
    public NumericDiagnostic.Result result(byte[] bytes, NumericDiagnostic.Request request) {
        if (bytes.length > 16384) throw new IllegalArgumentException("Diagnostic response too large");
        try {
            var n = mapper.readTree(bytes);
            if (n == null || !n.isObject()) throw new IllegalArgumentException("Expected diagnostic object");
            var fields = new HashSet<String>(); n.fieldNames().forEachRemaining(fields::add);
            if (!fields.equals(Set.of("schemaVersion", "executionId", "correlationId", "scopeId", "method", "policyVersion",
                "status", "reasonCode", "restrictionIds", "attempted", "admissible", "evaluated", "agreeing", "disagreeing", "unstable"))
                || !NumericDiagnostic.RESPONSE_VERSION.equals(text(n, "schemaVersion"))
                || !NumericDiagnostic.POLICY.equals(text(n, "policyVersion")) || !"NUMERIC_SAMPLING".equals(text(n, "method")))
                throw new IllegalArgumentException("Invalid diagnostic contract");
            if (!request.input().executionId().toString().equals(text(n, "executionId"))
                || !request.scopeId().toString().equals(text(n, "scopeId")))
                throw new IllegalArgumentException("Noncanonical diagnostic identity");
            var ids = new ArrayList<String>();
            if (!n.path("restrictionIds").isArray() || n.path("restrictionIds").size() > 48)
                throw new IllegalArgumentException("Invalid restriction references");
            for (var id : n.path("restrictionIds")) {
                if (!id.isTextual()) throw new IllegalArgumentException("Invalid restriction reference");
                ids.add(id.textValue());
            }
            var result = new NumericDiagnostic.Result(UUID.fromString(text(n, "executionId")), text(n, "correlationId"),
                UUID.fromString(text(n, "scopeId")), NumericDiagnostic.Status.valueOf(text(n, "status")),
                NumericDiagnostic.Reason.valueOf(text(n, "reasonCode")), ids, count(n, "attempted"), count(n, "admissible"),
                count(n, "evaluated"), count(n, "agreeing"), count(n, "disagreeing"), count(n, "unstable"));
            result.validateFor(request); return result;
        } catch (java.io.IOException e) { throw new IllegalArgumentException("Malformed diagnostic response"); }
    }
    private static String text(JsonNode n, String field) {
        if (!n.path(field).isTextual()) throw new IllegalArgumentException("Expected text");
        return n.path(field).textValue();
    }
    private static int count(JsonNode n, String field) {
        var value = n.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) throw new IllegalArgumentException("Expected sample count");
        return value.intValue();
    }
}
