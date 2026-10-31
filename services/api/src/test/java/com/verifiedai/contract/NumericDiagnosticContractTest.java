package com.verifiedai.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verifiedai.verification.NumericTestData;
import com.verifiedai.verification.infrastructure.http.NumericWireCodec;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "VERIFIEDAI_MATH_WIRE", matches = "true")
class NumericDiagnosticContractTest {
    @Test void actualJavaPythonHttpPreservesNonproofStatusAndEveryFixtureScope() throws Exception {
        String python = Objects.requireNonNull(System.getenv("VERIFIEDAI_VERIFIER_PYTHON"));
        var builder = new ProcessBuilder(python, "tests/contract/calculus_http_server.py")
            .directory(Path.of("../math-verifier").toFile()).redirectError(ProcessBuilder.Redirect.INHERIT);
        builder.environment().put("APP_ENV", "test");
        builder.environment().put("MATH_VERIFIER_NUMERIC_ENABLED", "true");
        builder.environment().put("MATH_VERIFIER_INTERNAL_TOKEN", "local_math_verifier_token_change_me");
        builder.environment().put("PYTHONPATH", ".");
        var process = builder.start(); var reader = Executors.newSingleThreadExecutor();
        try {
            int port = Integer.parseInt(reader.submit(() -> new BufferedReader(new InputStreamReader(process.getInputStream())).readLine()).get(10, TimeUnit.SECONDS));
            var endpoint = URI.create("http://127.0.0.1:" + port + "/internal/v1/verify/numeric");
            var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build();
            var mapper = new ObjectMapper(); var codec = new NumericWireCodec();
            for (var fixture : NumericTestData.cases()) {
                var request = NumericTestData.request(fixture.path("request"));
                var bytes = codec.request(request);
                assertThat(mapper.readTree(bytes)).isEqualTo(fixture.path("request"));
                var http = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json").header("X-Internal-Token", "local_math_verifier_token_change_me")
                    .header("X-Request-Id", request.input().correlationId()).POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
                var response = client.send(http, HttpResponse.BodyHandlers.ofByteArray());
                assertThat(response.statusCode()).as(fixture.path("id").textValue()).isEqualTo(200);
                assertThat(response.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
                var result = codec.result(response.body(), request);
                assertThat(result.status().name()).as(fixture.path("id").textValue()).isEqualTo(fixture.path("expectedStatus").textValue());
                assertThat(result.reasonCode().name()).isEqualTo(fixture.path("expectedReason").textValue());
                var forged = (ObjectNode) mapper.readTree(response.body()); forged.put("scopeId", "00000000-0000-0000-0000-000000000099");
                assertThatIllegalArgumentException().isThrownBy(() -> codec.result(mapper.writeValueAsBytes(forged), request));
            }
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); reader.shutdownNow();
        }
    }
}
