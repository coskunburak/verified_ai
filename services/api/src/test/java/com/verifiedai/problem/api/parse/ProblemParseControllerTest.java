package com.verifiedai.problem.api.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verifiedai.identity.application.AppleSignInCommand;
import com.verifiedai.identity.application.AuthSessionResult;
import com.verifiedai.identity.application.IdentityApplicationService;
import com.verifiedai.identity.domain.model.AppleIdentityVerifier;
import com.verifiedai.identity.domain.model.VerifiedAppleIdentity;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.problem.application.parse.ProblemParseApplicationService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@Import(ProblemParseControllerTest.AuthTestConfiguration.class)
@TestPropertySource(properties = "app.problem-parser.worker-interval=PT1H")
final class ProblemParseControllerTest extends PostgresIntegrationTestSupport {
    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");

    @Value("${local.server.port}")
    int port;

    @Autowired
    IdentityApplicationService identityApplicationService;

    @Autowired
    ProblemParseApplicationService parseApplicationService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("""
            truncate table
                canonical_problems,
                problem_parses,
                problem_parse_jobs,
                recognition_evidence,
                recognition_jobs,
                problem_asset_quality_evidence,
                problem_asset_derivatives,
                problem_assets,
                problem_sessions,
                entitlements,
                learning_profiles,
                auth_security_events,
                refresh_tokens,
                sessions,
                user_identities,
                users
            cascade
            """);
    }

    @Test
    void parseEndpointRequiresAuthentication() throws Exception {
        HttpResponse<String> response = httpClient.send(
            HttpRequest.newBuilder(uri("/api/v1/problem-sessions/" + UUID.randomUUID() + "/parse"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":\"AUTH_TOKEN_EXPIRED\"");
    }

    @Test
    void authenticatedUserCanRequestAndPollProblemParseWithoutRawParserOutput() throws Exception {
        AuthSessionResult session = signIn("problem-parse-api-user");
        UUID sessionId = insertRecognizedProblem(session.userId(), "x^2 + 3x = 10");

        HttpResponse<String> request = httpClient.send(
            authorized(session, "/api/v1/problem-sessions/" + sessionId + "/parse")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(request.statusCode()).as(request.body()).isEqualTo(202);
        JsonNode requested = objectMapper.readTree(request.body());
        assertThat(requested.path("jobStatus").asText()).isEqualTo("QUEUED");
        assertThat(requested.path("capability").asText()).isEqualTo("PROBLEM_NORMALIZE");
        assertThat(request.body()).doesNotContain("rawOutput");
        assertThat(request.body()).doesNotContain("safeVerifierAst");

        assertThat(parseApplicationService.runDueParseJobs(10)).isEqualTo(1);

        HttpResponse<String> current = httpClient.send(
            authorized(session, "/api/v1/problem-sessions/" + sessionId + "/parse")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(current.statusCode()).as(current.body()).isEqualTo(200);
        JsonNode parsed = objectMapper.readTree(current.body());
        assertThat(parsed.path("jobStatus").asText()).isEqualTo("SUCCEEDED");
        assertThat(parsed.path("supportStatus").asText()).isEqualTo("SUPPORTED");
        assertThat(parsed.path("schemaVersion").asText()).isEqualTo("problem-parse-v1");
        assertThat(parsed.path("promptId").asText()).isEqualTo("problem-parser");
        assertThat(parsed.path("normalizedProblem").path("taskType").asText()).isEqualTo("SOLVE_EQUATION");
        assertThat(parsed.path("createdAt").asText()).isNotBlank();
        assertThat(parsed.path("updatedAt").asText()).isNotBlank();
        assertThat(current.body()).doesNotContain("raw_output_jsonb");
        assertThat(current.body()).doesNotContain("primarySkill");
        assertThat(count("problem_parses")).isEqualTo(1);
    }

    @Test
    void authenticatedUserCanCanonicalizeSupportedParseWithoutAstExposure() throws Exception {
        AuthSessionResult session = signIn("canonical-problem-api-user");
        UUID sessionId = insertRecognizedProblem(session.userId(), "x + 1 = 2");

        httpClient.send(
            authorized(session, "/api/v1/problem-sessions/" + sessionId + "/parse")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(parseApplicationService.runDueParseJobs(10)).isEqualTo(1);

        HttpResponse<String> canonicalize = httpClient.send(
            authorized(session, "/api/v1/problem-sessions/" + sessionId + "/canonicalize")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(canonicalize.statusCode()).as(canonicalize.body()).isEqualTo(201);
        JsonNode created = objectMapper.readTree(canonicalize.body());
        assertThat(created.path("schemaVersion").asText()).isEqualTo("canonical-problem-v1");
        assertThat(created.path("verifierSchemaVersion").asText()).isEqualTo("verifier-input-v1");
        assertThat(created.path("problemType").asText()).isEqualTo("EQUATION");
        assertThat(canonicalize.body()).doesNotContain("canonicalProblemJson");
        assertThat(canonicalize.body()).doesNotContain("verifierInputJson");
        assertThat(canonicalize.body()).doesNotContain("\"kind\"");

        HttpResponse<String> current = httpClient.send(
            authorized(session, "/api/v1/problem-sessions/" + sessionId + "/canonical-problem")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(current.statusCode()).as(current.body()).isEqualTo(200);
        JsonNode parsed = objectMapper.readTree(current.body());
        assertThat(parsed.path("canonicalProblemId").asText()).isEqualTo(created.path("canonicalProblemId").asText());
        assertThat(count("canonical_problems")).isEqualTo(1);
    }

    @Test
    void authenticatedUserCanReviewCorrectAndCanonicalizeSelectedParseRevision() throws Exception {
        AuthSessionResult session = signIn("parse-correction-api-user");
        UUID sessionId = insertRecognizedProblem(session.userId(), "x + 1 = 2");

        httpClient.send(
            authorized(session, "/api/v1/problem-sessions/" + sessionId + "/parse")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(parseApplicationService.runDueParseJobs(10)).isEqualTo(1);

        HttpResponse<String> reviewResponse = httpClient.send(
            authorized(session, "/api/v1/problem-sessions/" + sessionId + "/parse-review")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(reviewResponse.statusCode()).as(reviewResponse.body()).isEqualTo(200);
        JsonNode review = objectMapper.readTree(reviewResponse.body());
        JsonNode currentParse = review.path("currentParse");
        assertThat(currentParse.path("source").asText()).isEqualTo("AI");
        assertThat(review.path("revisionCount").asInt()).isEqualTo(1);
        assertThat(review.path("canCorrect").asBoolean()).isTrue();
        assertThat(reviewResponse.body()).doesNotContain("rawOutput");
        assertThat(reviewResponse.body()).doesNotContain("idempotency");

        ObjectNode correctedProblem = currentParse.path("normalizedProblem").deepCopy();
        ObjectNode expression = (ObjectNode) correctedProblem.path("expressions").get(0);
        expression.put("sourceText", "x + 2 = 5");
        expression.put("normalizedText", "x + 2 = 5");
        expression.put("displayLatex", "x + 2 = 5");

        ObjectNode correctionRequest = objectMapper.createObjectNode();
        correctionRequest.put("baseParseId", currentParse.path("problemParseId").asText());
        correctionRequest.put("baseRevision", currentParse.path("revision").asInt());
        correctionRequest.put("correctionReason", "MATH_EXPRESSION_ERROR");
        correctionRequest.set("problem", correctedProblem);
        String idempotencyKey = "parse-correction-api-test-" + sessionId;

        HttpResponse<String> correctionResponse = httpClient.send(
            authorized(session, "/api/v1/problem-sessions/" + sessionId + "/parse-revisions")
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(correctionRequest)))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(correctionResponse.statusCode()).as(correctionResponse.body()).isEqualTo(201);
        JsonNode correction = objectMapper.readTree(correctionResponse.body());
        assertThat(correction.path("source").asText()).isEqualTo("USER");
        assertThat(correction.path("revision").asInt()).isEqualTo(2);
        assertThat(correction.path("parentParseId").asText()).isEqualTo(currentParse.path("problemParseId").asText());
        assertThat(correction.path("selected").asBoolean()).isTrue();
        assertThat(correction.path("canonicalizationRequired").asBoolean()).isTrue();

        HttpResponse<String> replayResponse = httpClient.send(
            authorized(session, "/api/v1/problem-sessions/" + sessionId + "/parse-revisions")
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(correctionRequest)))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(replayResponse.statusCode()).as(replayResponse.body()).isEqualTo(201);
        JsonNode replay = objectMapper.readTree(replayResponse.body());
        assertThat(replay.path("problemParseId").asText()).isEqualTo(correction.path("problemParseId").asText());
        assertThat(count("problem_parses")).isEqualTo(2);

        HttpResponse<String> updatedReviewResponse = httpClient.send(
            authorized(session, "/api/v1/problem-sessions/" + sessionId + "/parse-review")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        JsonNode updatedReview = objectMapper.readTree(updatedReviewResponse.body());
        assertThat(updatedReview.path("currentParse").path("source").asText()).isEqualTo("USER");
        assertThat(updatedReview.path("currentParse").path("revision").asInt()).isEqualTo(2);
        assertThat(updatedReview.path("currentParse").path("normalizedProblem").path("expressions").get(0).path("normalizedText").asText())
            .isEqualTo("x + 2 = 5");

        HttpResponse<String> historyResponse = httpClient.send(
            authorized(session, "/api/v1/problem-sessions/" + sessionId + "/parse-revisions")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(historyResponse.statusCode()).as(historyResponse.body()).isEqualTo(200);
        JsonNode history = objectMapper.readTree(historyResponse.body());
        assertThat(history.path("selectedParseId").asText()).isEqualTo(correction.path("problemParseId").asText());
        assertThat(history.path("revisions").size()).isEqualTo(2);
        assertThat(history.path("revisions").get(0).path("correctedFieldCategories").toString()).contains("EXPRESSION");
        assertThat(historyResponse.body()).doesNotContain("correctionRequestHash");
        assertThat(historyResponse.body()).doesNotContain("Idempotency");

        HttpResponse<String> canonicalize = httpClient.send(
            authorized(session, "/api/v1/problem-sessions/" + sessionId + "/canonicalize")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(canonicalize.statusCode()).as(canonicalize.body()).isEqualTo(201);
        JsonNode canonical = objectMapper.readTree(canonicalize.body());
        assertThat(canonical.path("problemParseId").asText()).isEqualTo(correction.path("problemParseId").asText());
        assertThat(canonical.path("problemParseRevision").asInt()).isEqualTo(2);
        assertThat(canonical.path("normalizedText").asText()).isEqualTo("x + 2 = 5");
    }

    private AuthSessionResult signIn(String subject) {
        return identityApplicationService.signInWithApple(new AppleSignInCommand(subject, "unused-code", "nonce"));
    }

    private HttpRequest.Builder authorized(AuthSessionResult session, String path) {
        return HttpRequest.newBuilder(uri(path))
            .header("Authorization", "Bearer " + session.accessToken())
            .header("Accept", "application/json");
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private UUID insertRecognizedProblem(UUID userId, String text) {
        UUID sessionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID derivativeId = UUID.randomUUID();
        UUID recognitionJobId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();
        byte[] sourceBytes = "source-image".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] derivativeBytes = "ocr-optimized-image".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String sourceKey = "problem-assets/" + sessionId + "/" + assetId + "/original";
        String derivativeKey = "problem-assets/" + sessionId + "/" + assetId + "/derivatives/" + derivativeId + "/ocr-optimized.jpg";
        jdbcTemplate.update(
            "insert into problem_sessions (id, user_id, status, input_mode, created_at, updated_at) values (?, ?, 'ASSET_UPLOADED', 'CAMERA', ?, ?)",
            sessionId,
            userId,
            Timestamp.from(NOW),
            Timestamp.from(NOW)
        );
        jdbcTemplate.update(
            """
            insert into problem_assets (
                id, problem_session_id, user_id, source_type, asset_kind, status, object_key, content_type,
                size_bytes, checksum_algorithm, checksum_value, crop_x, crop_y, crop_width, crop_height,
                image_width, image_height, page_count, retention_class, upload_expires_at, available_at,
                created_at, updated_at, reservation_idempotency_key, reservation_request_hash
            )
            values (?, ?, ?, 'CAMERA', 'IMAGE', 'AVAILABLE', ?, 'image/jpeg', ?, 'SHA-256', ?, 0, 0, 1, 1,
                    1200, 900, null, 'TEMPORARY_RAW', ?, ?, ?, ?, ?, ?)
            """,
            assetId,
            sessionId,
            userId,
            sourceKey,
            (long) sourceBytes.length,
            sha256Hex(sourceBytes),
            Timestamp.from(NOW.plus(Duration.ofMinutes(15))),
            Timestamp.from(NOW),
            Timestamp.from(NOW),
            Timestamp.from(NOW),
            "reserve-" + assetId,
            "0".repeat(64)
        );
        jdbcTemplate.update(
            """
            insert into problem_asset_derivatives (
                id, source_asset_id, problem_session_id, user_id, derivative_kind, status, selected_for_recognition,
                object_key, content_type, size_bytes, checksum_algorithm, checksum_value, width, height,
                source_width, source_height, crop_x, crop_y, crop_width, crop_height, processor_name,
                processor_version, configuration_version, orientation_normalized, perspective_applied,
                contrast_normalized, resized, quality_outcome, created_at, updated_at, completed_at
            )
            values (?, ?, ?, ?, 'OCR_OPTIMIZED', 'READY', true, ?, 'image/jpeg', ?, 'SHA-256', ?, 1200, 900,
                    1200, 900, 0, 0, 1, 1, 'DOCUMENT_PREPROCESSOR', '1.0', 'capture-quality-v1',
                    false, false, false, false, 'PASS', ?, ?, ?)
            """,
            derivativeId,
            assetId,
            sessionId,
            userId,
            derivativeKey,
            (long) derivativeBytes.length,
            sha256Hex(derivativeBytes),
            Timestamp.from(NOW),
            Timestamp.from(NOW),
            Timestamp.from(NOW)
        );
        jdbcTemplate.update(
            """
            insert into recognition_jobs (
                id, user_id, problem_session_id, source_asset_id, input_derivative_id, status, capability,
                prompt_id, prompt_version, schema_version, route_policy_version, attempt_count, max_attempts,
                next_attempt_at, review_required, created_at, updated_at, started_at, completed_at
            )
            values (?, ?, ?, ?, ?, 'SUCCEEDED', 'VISION_PARSE', 'vision-recognition', 'v001',
                    'recognition-evidence-v1', 'vision-route-v1', 1, 2, ?, false, ?, ?, ?, ?)
            """,
            recognitionJobId,
            userId,
            sessionId,
            assetId,
            derivativeId,
            Timestamp.from(NOW),
            Timestamp.from(NOW),
            Timestamp.from(NOW),
            Timestamp.from(NOW),
            Timestamp.from(NOW)
        );
        jdbcTemplate.update(
            """
            insert into recognition_evidence (
                id, recognition_job_id, user_id, problem_session_id, source_asset_id, input_derivative_id,
                revision, capability, schema_version, raw_output_jsonb, normalized_evidence_jsonb,
                upstream_quality_evidence_jsonb, provider, model, route_policy_version, prompt_id,
                prompt_version, input_tokens, output_tokens, image_units, request_units, provider_latency_ms,
                total_latency_ms, estimated_cost_micros, currency, pricing_version, created_at
            )
            values (?, ?, ?, ?, ?, ?, 1, 'VISION_PARSE', 'recognition-evidence-v1',
                    ?::jsonb, ?::jsonb, ?::jsonb, 'TEST_PROVIDER', 'test-vision-v1', 'vision-route-v1',
                    'vision-recognition', 'v001', 12, 24, 1, 1, 7, 9, 42, 'USD', 'test-pricing-v1', ?)
            """,
            evidenceId,
            recognitionJobId,
            userId,
            sessionId,
            assetId,
            derivativeId,
            recognitionJson(text),
            normalizedRecognitionJson(text),
            upstreamQualityJson(),
            Timestamp.from(NOW)
        );
        return sessionId;
    }

    private static String recognitionJson(String text) {
        return """
            {"schemaVersion":"recognition-evidence-v1","blocks":[{"id":"block-1","kind":"MATH","text":"%s","boundingBox":{"x":0.1,"y":0.2,"width":0.7,"height":0.2},"readingOrder":0,"confidence":{"raw":0.98,"normalized":0.98,"scale":"0_TO_1"},"uncertainty":[],"layoutHints":["INLINE_MATH"]}],"documentUncertainty":[],"reviewRequired":false}
            """.formatted(text.replace("\"", "\\\""));
    }

    private static String normalizedRecognitionJson(String text) {
        return """
            {"schemaVersion":"recognition-evidence-v1","coordinateSpace":{"space":"INPUT_ASSET_NORMALIZED","version":"input-asset-normalized-v1","inputAssetId":"fixture","width":1200,"height":900},"blocks":[{"id":"block-1","kind":"MATH","text":"%s","boundingBox":{"x":0.100000,"y":0.200000,"width":0.700000,"height":0.200000},"readingOrder":0,"confidence":{"status":"KNOWN","normalized":0.980000,"rawProviderConfidence":0.98},"uncertainty":[],"layoutHints":["INLINE_MATH"]}],"documentUncertainty":[],"upstreamQualityEvidence":[],"reviewRequired":false,"canonicalProblemCreated":false}
            """.formatted(text.replace("\"", "\\\""));
    }

    private static String upstreamQualityJson() {
        return """
            {"qualitySignals":[{"signalType":"RESOLUTION","severity":"PASS","score":1200,"threshold":900,"policyVersion":"capture-quality-v1","messageCode":"CAPTURE_RESOLUTION_PASS"}]}
            """;
    }

    private Integer count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @TestConfiguration
    static class AuthTestConfiguration {
        @Bean
        @Primary
        AppleIdentityVerifier appleIdentityVerifier() {
            return (identityToken, rawNonce) -> new VerifiedAppleIdentity(identityToken);
        }
    }
}
