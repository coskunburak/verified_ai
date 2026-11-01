package com.verifiedai.ai.infrastructure.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import com.verifiedai.ai.GeminiTestPolicy;
import com.verifiedai.ai.application.*;
import com.verifiedai.ai.infrastructure.configuration.GeminiProviderProperties;
import com.verifiedai.solving.SolverTestData;
import com.verifiedai.solving.application.SolutionCandidateValidator;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import static org.assertj.core.api.Assertions.*;

class GeminiAiProviderAdapterTest {

    final ObjectMapper mapper = new ObjectMapper();
    final com.verifiedai.problem.application.solving.SolveSource source = SolverTestData.source();
    HttpServer server;
    GeminiAiProviderAdapter adapter;
    ObjectNode geminiResponse;
    volatile int httpStatus = 200;
    final AtomicReference<String> capturedApiKey = new AtomicReference<>();
    final AtomicReference<ObjectNode> capturedRequestBody = new AtomicReference<>();
    final AtomicInteger callCount = new AtomicInteger();

    @BeforeEach
    void setup() throws Exception {
        var candidate = SolverTestData.candidate(source);
        candidate.put("generationOrigin", "AI_MODEL");

        geminiResponse = mapper.createObjectNode();
        geminiResponse.put("modelVersion", GeminiTestPolicy.MODEL);
        geminiResponse.put("responseId", "gemini-resp-test-12345");

        var candArray = geminiResponse.putArray("candidates");
        var cand = candArray.addObject();
        cand.put("finishReason", "STOP");
        cand.put("index", 0);
        var content = cand.putObject("content");
        content.put("role", "model");
        content.putArray("parts").addObject().put("text", candidate.toString());

        var usage = geminiResponse.putObject("usageMetadata");
        usage.put("promptTokenCount", 120);
        usage.put("candidatesTokenCount", 240);
        usage.put("totalTokenCount", 360);

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        // Mock generateContent endpoint
        server.createContext("/v1beta/models/" + GeminiTestPolicy.MODEL + ":generateContent", exchange -> {
            callCount.incrementAndGet();
            capturedApiKey.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            capturedRequestBody.set((ObjectNode) mapper.readTree(exchange.getRequestBody()));

            byte[] body = geminiResponse.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(httpStatus, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
            exchange.close();
        });

        // Mock models GET health endpoint
        server.createContext("/v1beta/models/" + GeminiTestPolicy.MODEL, exchange -> {
            byte[] body = ("{\"name\":\"models/" + GeminiTestPolicy.MODEL + "\",\"displayName\":\"Gemini 2.5 Flash\"}")
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
            exchange.close();
        });

        server.start();

        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        adapter = new GeminiAiProviderAdapter(
            GeminiTestPolicy.config(endpoint),
            Clock.systemUTC(),
            new DefaultResourceLoader(),
            PromptRegistry.defaults(),
            OutputSchemaRegistry.defaults(),
            "test"
        );
    }

    @AfterEach
    void tearDown() {
        if (adapter != null) adapter.close();
        if (server != null) server.stop(0);
    }

    AiProviderRequest createRequest(long budget, Instant deadline, Duration timeout) {
        return new AiProviderRequest(
            GeminiTestPolicy.command(source, budget, deadline),
            GeminiTestPolicy.plan(),
            GeminiTestPolicy.plan().primary(),
            timeout,
            budget
        );
    }

    AiSolveResult executeSolve() {
        return (AiSolveResult) adapter.execute(createRequest(100L, Instant.now().plusSeconds(30), Duration.ofSeconds(30)));
    }

    @Test
    void validSolveRequestProducesCandidateAndPassesMathematicalValidator() {
        AiSolveResult result = executeSolve();

        assertThat(result).isNotNull();
        assertThat(capturedApiKey.get()).isEqualTo(GeminiTestPolicy.CREDENTIAL);
        assertThat(callCount.get()).isEqualTo(1);

        // Check request payload structure
        ObjectNode req = capturedRequestBody.get();
        assertThat(req).isNotNull();
        assertThat(req.path("generationConfig").path("responseMimeType").asText()).isEqualTo("application/json");
        assertThat(req.path("generationConfig").path("maxOutputTokens").asInt()).isEqualTo(8192);

        // Check system instruction contains Turkish explanation directive and schema
        String sysText = req.path("systemInstruction").path("parts").get(0).path("text").asText();
        assertThat(sysText).contains("Public explanation language: tr");
        assertThat(sysText).contains("solution-candidate-v1");

        // Validate the candidate through the mathematical solution validator
        var validator = new SolutionCandidateValidator();
        var candidate = validator.validate(result.rawOutputJson(), source, adapter.providerId());
        assertThat(candidate).isNotNull();
        assertThat(candidate.ambiguities()).isEmpty();
        assertThat(candidate.unresolvedIssues()).isEmpty();

        // Verify provenance and usage
        assertThat(result.provenance().provider()).isEqualTo(GeminiProviderProperties.PROVIDER);
        assertThat(result.provenance().model()).isEqualTo(GeminiTestPolicy.MODEL);
        assertThat(result.usage().inputTokens()).isEqualTo(120);
        assertThat(result.usage().outputTokens()).isEqualTo(240);
        assertThat(result.usage().estimatedCostMicros()).isEqualTo(100L);
    }

    @Test
    void redactionPreservesZeroSecretPolicyInExceptions() {
        httpStatus = 401;

        assertThatThrownBy(this::executeSolve)
            .isInstanceOfSatisfying(AiProviderException.class, e -> {
                assertThat(e.failureClass()).isEqualTo(AiProviderFailureClass.INVALID_AUTH);
                assertThat(e.retryable()).isFalse();
                // Ensure synthetic key is never leaked into exception messages
                assertThat(e.getMessage()).doesNotContain(GeminiTestPolicy.CREDENTIAL);
            });
    }

    @Test
    void safetyFinishReasonIsClassifiedAsPolicyBlocked() {
        ((ObjectNode) geminiResponse.path("candidates").get(0)).put("finishReason", "SAFETY");

        assertThatThrownBy(this::executeSolve)
            .isInstanceOfSatisfying(AiProviderException.class, e -> {
                assertThat(e.failureClass()).isEqualTo(AiProviderFailureClass.POLICY_BLOCKED);
                assertThat(e.retryable()).isFalse();
            });
    }

    @Test
    void maxTokensFinishReasonIsClassifiedAsOutputTooLarge() {
        ((ObjectNode) geminiResponse.path("candidates").get(0)).put("finishReason", "MAX_TOKENS");

        assertThatThrownBy(this::executeSolve)
            .isInstanceOfSatisfying(AiProviderException.class, e -> {
                assertThat(e.failureClass()).isEqualTo(AiProviderFailureClass.OUTPUT_TOO_LARGE);
                assertThat(e.retryable()).isFalse();
            });
    }

    @Test
    void httpRateLimitIsClassifiedAsRateLimited() {
        httpStatus = 429;

        assertThatThrownBy(this::executeSolve)
            .isInstanceOfSatisfying(AiProviderException.class, e -> {
                assertThat(e.failureClass()).isEqualTo(AiProviderFailureClass.RATE_LIMITED);
                assertThat(e.retryable()).isFalse();
            });
    }

    @Test
    void httpServerErrorIsClassifiedAsProviderUnavailable() {
        httpStatus = 503;

        assertThatThrownBy(this::executeSolve)
            .isInstanceOfSatisfying(AiProviderException.class, e -> {
                assertThat(e.failureClass()).isEqualTo(AiProviderFailureClass.PROVIDER_UNAVAILABLE);
                assertThat(e.retryable()).isFalse();
            });
    }

    @Test
    void healthCheckDetectsHealthyModel() {
        var target = new AiRouteTarget(GeminiProviderProperties.PROVIDER, GeminiTestPolicy.MODEL);
        var snapshot = adapter.health(target);

        assertThat(snapshot.state()).isEqualTo(AiProviderHealth.State.CLOSED);
        assertThat(snapshot.usable(Instant.now(), Duration.ofSeconds(30))).isTrue();
    }
}
