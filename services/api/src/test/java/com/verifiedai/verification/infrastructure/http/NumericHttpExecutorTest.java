package com.verifiedai.verification.infrastructure.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import com.verifiedai.verification.NumericTestData;
import com.verifiedai.verification.domain.model.NumericDiagnostic;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NumericHttpExecutorTest {
    NumericDiagnostic.Request request() throws Exception { return NumericTestData.request(NumericTestData.cases().get(0).path("request")); }
    ObjectNode response() throws Exception {
        var r = request(); var n = new ObjectMapper().createObjectNode();
        n.put("schemaVersion", NumericDiagnostic.RESPONSE_VERSION).put("executionId", r.input().executionId().toString())
            .put("correlationId", r.input().correlationId()).put("scopeId", r.scopeId().toString()).put("method", "NUMERIC_SAMPLING")
            .put("policyVersion", NumericDiagnostic.POLICY).put("status", "AGREEMENT").put("reasonCode", "SAMPLES_AGREE")
            .put("attempted", 16).put("admissible", 16).put("evaluated", 16).put("agreeing", 16).put("disagreeing", 0).put("unstable", 0);
        n.putArray("restrictionIds"); return n;
    }
    @Test void wireRejectsForgedProofUnknownFieldsVersionsCorrelationCountsAndBounds() throws Exception {
        var codec = new NumericWireCodec(); var request = request();
        for (var field : List.of("schemaVersion", "policyVersion", "method", "executionId", "scopeId", "correlationId", "status", "reasonCode")) {
            var n = response(); n.put(field, "forged");
            assertThatIllegalArgumentException().isThrownBy(() -> codec.result(n.toString().getBytes(StandardCharsets.UTF_8), request));
        }
        for (var field : List.of("proof", "unknown")) {
            var n = response(); n.putNull(field);
            assertThatIllegalArgumentException().isThrownBy(() -> codec.result(n.toString().getBytes(StandardCharsets.UTF_8), request));
        }
        for (var field : List.of("schemaVersion", "method", "policyVersion", "scopeId", "unstable")) {
            var n = response(); n.remove(field);
            assertThatIllegalArgumentException().isThrownBy(() -> codec.result(n.toString().getBytes(StandardCharsets.UTF_8), request));
        }
        var badCounts = response(); badCounts.put("disagreeing", 1);
        assertThatIllegalArgumentException().isThrownBy(() -> codec.result(badCounts.toString().getBytes(StandardCharsets.UTF_8), request));
        var fractional = response(); fractional.put("attempted", 16.0);
        assertThatIllegalArgumentException().isThrownBy(() -> codec.result(fractional.toString().getBytes(StandardCharsets.UTF_8), request));
        assertThatIllegalArgumentException().isThrownBy(() -> codec.result(new byte[16385], request));
        String duplicate = response().toString().replace("\"attempted\":16", "\"attempted\":16,\"attempted\":16");
        assertThatIllegalArgumentException().isThrownBy(() -> codec.result(duplicate.getBytes(StandardCharsets.UTF_8), request));
    }
    @Test void transportUsesAuthenticationHttp11AndSharedResponseBound() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var body = new AtomicReference<>(response().toString());
        var protocol = new AtomicReference<String>(); var token = new AtomicReference<String>();
        server.createContext("/internal/v1/verify/numeric", exchange -> {
            protocol.set(exchange.getProtocol()); token.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            exchange.getRequestBody().readAllBytes();
            var bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            var adapter = new HttpMathematicalExecutor("http://127.0.0.1:" + server.getAddress().getPort(), "not_a_secret", false, "test");
            var request = request();
            assertThat(adapter.diagnose(request).reasonCode()).isEqualTo(NumericDiagnostic.Reason.DISABLED);
            adapter.configureNumeric(true, false);
            assertThat(adapter.diagnose(request).reasonCode()).isEqualTo(NumericDiagnostic.Reason.DISABLED);
            adapter.configureNumeric(true, true);
            assertThat(adapter.diagnose(request).status()).isEqualTo(NumericDiagnostic.Status.AGREEMENT);
            assertThat(protocol.get()).isEqualTo("HTTP/1.1"); assertThat(token.get()).isEqualTo("not_a_secret");
            body.set("x".repeat(16385));
            assertThat(adapter.diagnose(request).reasonCode()).isEqualTo(NumericDiagnostic.Reason.INVALID_RESPONSE);
            body.set("{}");
            assertThat(adapter.diagnose(request).reasonCode()).isEqualTo(NumericDiagnostic.Reason.INVALID_RESPONSE);
        } finally { server.stop(0); }
    }
    @Test void productionActivationIsRejectedEvenWithOwnerAcceptance() {
        var adapter = new HttpMathematicalExecutor("http://math-verifier:8090", "not_a_secret", false, "production");
        assertThatIllegalArgumentException().isThrownBy(() -> adapter.configureNumeric(true, true));
    }
}
