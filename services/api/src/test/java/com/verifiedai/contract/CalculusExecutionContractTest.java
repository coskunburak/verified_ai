package com.verifiedai.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.verification.infrastructure.http.CalculusWireCodec;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

/** Gate D: actual Java bytes -> authenticated Python HTTP -> spawned bounded worker -> Java validation. */
@EnabledIfEnvironmentVariable(named = "VERIFIEDAI_MATH_WIRE", matches = "true")
class CalculusExecutionContractTest {
    @Test void actualHttpCalculusContractPreservesEveryFixtureScopeAndProof() throws Exception {
        String python = Objects.requireNonNull(System.getenv("VERIFIEDAI_VERIFIER_PYTHON"));
        var builder = new ProcessBuilder(python, "tests/contract/calculus_http_server.py")
            .directory(Path.of("../math-verifier").toFile()).redirectError(ProcessBuilder.Redirect.INHERIT);
        builder.environment().put("APP_ENV", "test");
        builder.environment().put("MATH_VERIFIER_CALCULUS_ENABLED", "true");
        builder.environment().put("MATH_VERIFIER_INTERNAL_TOKEN", "local_math_verifier_token_change_me");
        builder.environment().put("PYTHONPATH", ".");
        var process = builder.start();
        var reader = Executors.newSingleThreadExecutor();
        try {
            var portLine = reader.submit(() -> new BufferedReader(new InputStreamReader(process.getInputStream())).readLine());
            int port = Integer.parseInt(portLine.get(10, TimeUnit.SECONDS));
            var endpoint = URI.create("http://127.0.0.1:" + port + "/internal/v1/verify/calculus");
            var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(2)).build();
            var mapper = new ObjectMapper();
            var fixtures = mapper.readTree(Files.readString(Path.of("../../packages/test-fixtures/verification/calculus-v1/cases.json")));
            var codec = new CalculusWireCodec();
            for (var fixture : fixtures) {
                var request = codec.request(mapper.writeValueAsBytes(fixture.path("request")));
                var requestBytes = codec.request(request);
                assertThat(mapper.readTree(requestBytes).equals(fixture.path("request")))
                    .as("%s serialized request matches the shared contract", fixture.path("id").textValue()).isTrue();
                var http = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json").header("X-Internal-Token", "local_math_verifier_token_change_me")
                    .header("X-Request-Id", request.correlationId()).POST(HttpRequest.BodyPublishers.ofByteArray(requestBytes)).build();
                var response = client.send(http, HttpResponse.BodyHandlers.ofByteArray());
                var contentType = response.headers().firstValue("Content-Type").orElse("");
                assertThat(response.statusCode()).as("%s (%s)", fixture.path("id").textValue(),
                    responseDiagnostic(contentType, response.body())).isEqualTo(200);
                assertThat(response.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
                assertThat(contentType.toLowerCase(Locale.ROOT)).startsWith("application/json");
                var result = codec.result(response.body(), request);
                assertThat(result.outcome().name()).as(fixture.path("id").textValue()).isEqualTo(fixture.path("expectedOutcome").textValue());
                assertThat(result.reasonCode().name()).isEqualTo(fixture.path("expectedReason").textValue());
                assertThat(result.proof() == null ? null : result.proof().descriptor().name()).isEqualTo(fixture.path("expectedDescriptor").isNull() ? null : fixture.path("expectedDescriptor").textValue());
                assertThat(result.scope()).isEqualTo(request.scope());
                assertThat(result.verifierVersion()).isEqualTo("bounded-calculus-v1");
                var malicious = mapper.readTree(response.body());
                ((com.fasterxml.jackson.databind.node.ObjectNode) malicious.path("scope")).put("sourceRevision", 2);
                assertThatIllegalArgumentException().isThrownBy(() -> codec.result(mapper.writeValueAsBytes(malicious), request));
            }
            var unauthorized = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(fixtures.get(0).path("request")))).build();
            assertThat(client.send(unauthorized, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS);
            reader.shutdownNow();
        }
    }
    @Test void responseDiagnosticsDoNotParsePlainTextOrEchoUntrustedBodies() {
        assertThat(responseDiagnostic("text/plain", "Invalid HTTP request received.".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            .isEqualTo("non-json response");
        assertThat(responseDiagnostic("application/json", "private malformed body".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            .isEqualTo("malformed-json response");
        assertThat(responseDiagnostic("application/json", "{\"code\":\"VERIFIER_INPUT_SCHEMA_INVALID\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            .isEqualTo("VERIFIER_INPUT_SCHEMA_INVALID");
        assertThat(responseDiagnostic("application/json", "{\"code\":\"private user content\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            .isEqualTo("no recognized verifier code");
    }
    private static String responseDiagnostic(String contentType, byte[] bytes) {
        if (!contentType.toLowerCase(Locale.ROOT).startsWith("application/json")) return "non-json response";
        if (bytes.length > 16384) return "oversized response";
        try {
            var tree = new ObjectMapper().readTree(bytes);
            var code = tree == null ? "" : tree.path("code").asText("");
            return Set.of("VERIFIER_INPUT_SCHEMA_INVALID", "VERIFIER_CORRELATION_MISMATCH", "VERIFIER_REQUEST_TOO_LARGE")
                .contains(code) ? code : "no recognized verifier code";
        } catch (java.io.IOException e) { return "malformed-json response"; }
    }
}
