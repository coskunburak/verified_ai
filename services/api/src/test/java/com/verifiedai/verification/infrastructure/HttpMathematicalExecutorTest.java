package com.verifiedai.verification.infrastructure;

import com.sun.net.httpserver.HttpServer;
import com.verifiedai.solving.domain.model.canonical.MathStatement;
import com.verifiedai.verification.domain.model.*;
import com.verifiedai.verification.domain.model.MathematicalExecution.*;
import com.verifiedai.verification.infrastructure.http.HttpMathematicalExecutor;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import static com.verifiedai.verification.PlanningTestData.number;
import static org.assertj.core.api.Assertions.*;

class HttpMathematicalExecutorTest {
    HttpServer server;
    final Request request = new Request(UUID.randomUUID(), "http-contract", VerificationMethod.ARITHMETIC_EVALUATION,
        new MathStatement.Expression(number("5")), new MathStatement.Expression(number("5")), Map.of(), List.of());
    @AfterEach void cleanup() { if (server != null) server.stop(0); }
    HttpMathematicalExecutor gateway(int status, String body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/verify/execute", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("X-Internal-Token")).isEqualTo("not_a_secret");
            assertThat(exchange.getRequestHeaders().getFirst("X-Request-Id")).isEqualTo(request.correlationId());
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        return new HttpMathematicalExecutor("http://127.0.0.1:" + server.getAddress().getPort(), "not_a_secret", true, "test");
    }
    @Test void transportAndCorruptedResponsesCannotCreateProof() throws Exception {
        var bad = gateway(503, "{}").execute(request);
        assertThat(bad.outcome()).isEqualTo(Outcome.EXECUTION_FAILED); assertThat(bad.proof()).isNull();
        server.stop(0);
        var corrupt = gateway(200, "{}").execute(request);
        assertThat(corrupt.reasonCode()).isEqualTo(Reason.INVALID_RESPONSE);
    }
    @Test void boundedResponsePreventsUnboundedBodyAllocation() throws Exception {
        assertThat(gateway(200, "x".repeat(17000)).execute(request).outcome()).isEqualTo(Outcome.EXECUTION_FAILED);
    }
    @Test void disabledExecutionAndUnsafeProductionCredentialsFailClosed() {
        assertThat(new HttpMathematicalExecutor("http://math-verifier:8090", "not_a_secret", false, "test")
            .execute(request).reasonCode()).isEqualTo(Reason.DISABLED);
        assertThatThrownBy(() -> new HttpMathematicalExecutor("http://math-verifier:8090", "short", true, "production"))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void authenticatedSuccessfulHttpResponseCarriesExactMethodEvidence() throws Exception {
        String body = """
            {"schemaVersion":"verification-execution-result-v1","executionId":"%s","correlationId":"http-contract",
             "method":"ARITHMETIC_EVALUATION","methodVersion":"v1","verifierVersion":"canonical-executor-v1",
             "outcome":"PROVEN","reasonCode":"EXACT_EQUAL","restrictionIds":[],
             "proof":{"descriptor":"EXACT_RATIONAL_COMPARISON","leftValue":{"numerator":"5","denominator":"1"},
                      "rightValue":{"numerator":"5","denominator":"1"},"distinctRootCount":null}}
            """.formatted(request.executionId());
        assertThat(gateway(200, body).execute(request).outcome()).isEqualTo(Outcome.PROVEN);
    }
    @Test @Timeout(10) void stalledResponseBodyIsBoundedByTotalDeadline() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/verify/execute", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 100);
            try (var output = exchange.getResponseBody()) {
                output.write('{'); output.flush();
                try { Thread.sleep(6000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        });
        server.start();
        var gateway = new HttpMathematicalExecutor("http://127.0.0.1:" + server.getAddress().getPort(), "not_a_secret", true, "test");
        assertThat(gateway.execute(request).reasonCode()).isEqualTo(Reason.TIMEOUT);
    }
}
