package com.verifiedai.admin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.verifiedai.admin.api.dto.QuotaSyncRemediationRequest;
import com.verifiedai.admin.api.dto.RawContentGrantRequest;
import com.verifiedai.admin.application.AdminRawContentAccessService;
import com.verifiedai.admin.application.AdminRemediationService;
import com.verifiedai.admin.application.AdminSupportMetrics;
import com.verifiedai.admin.application.AdminSupportQueryService;
import com.verifiedai.admin.application.WrongAnswerReportService;
import com.verifiedai.admin.infrastructure.AdminSupportProperties;
import com.verifiedai.admin.infrastructure.AdminSupportReadJdbcRepository;
import com.verifiedai.admin.infrastructure.AdminSupportWriteJdbcRepository;
import com.verifiedai.billing.application.AppleBillingApplicationService;
import com.verifiedai.billing.application.EntitlementApplicationService;
import com.verifiedai.billing.application.QuotaLedgerService;
import com.verifiedai.featureflags.application.FeatureFlagGovernanceService;
import com.verifiedai.identity.application.AppleSignInCommand;
import com.verifiedai.identity.application.AuthSessionResult;
import com.verifiedai.identity.application.IdentityApplicationService;
import com.verifiedai.identity.domain.model.AppleIdentityVerifier;
import com.verifiedai.identity.domain.model.VerifiedAppleIdentity;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.sharedkernel.error.ApiExceptionHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Import(AdminSupportConsoleSecurityIT.AuthTestConfiguration.class)
@TestPropertySource(properties = {
    "verifiedai.admin.console-enabled=true",
    "verifiedai.admin.remediation-enabled=true",
    "verifiedai.admin.raw-content-access-enabled=true"
})
final class AdminSupportConsoleSecurityIT extends PostgresIntegrationTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();

    @Value("${local.server.port}")
    int port;

    @Autowired
    IdentityApplicationService identityApplicationService;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    AdminSupportReadJdbcRepository readRepository;

    @Autowired
    AdminSupportWriteJdbcRepository writeRepository;

    @Autowired
    AppleBillingApplicationService appleBillingApplicationService;

    @Autowired
    EntitlementApplicationService entitlementApplicationService;

    @Autowired
    QuotaLedgerService quotaLedgerService;

    @Autowired
    FeatureFlagGovernanceService featureFlagGovernanceService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void resetState() {
        jdbcTemplate.execute("""
            truncate table
                admin_raw_content_access_grants,
                wrong_answer_reports,
                admin_support_audit_logs,
                ai_usage_records,
                problem_sessions,
                refresh_tokens,
                sessions,
                user_identities,
                users
            cascade
            """);
    }

    @Test
    void enforcesAuthnRbacFailClosedDefaultsBolaAndDefaultRedactionWithElevatedRawAccess() throws Exception {
        // 1. Verify @PreAuthorize("hasRole('ADMIN')") across admin controller methods
        assertAdminRoleAnnotation(AdminAiOperationsController.class.getMethod(
            "getAiOpsOverview", Jwt.class, org.springframework.security.core.Authentication.class, String.class, String.class
        ));
        assertAdminRoleAnnotation(AdminSupportConsoleController.class.getMethod(
            "lookupUser", Jwt.class, org.springframework.security.core.Authentication.class, String.class, String.class
        ));
        assertAdminRoleAnnotation(AdminSupportConsoleController.class.getMethod(
            "getBillingSupport", Jwt.class, org.springframework.security.core.Authentication.class, UUID.class
        ));
        assertAdminRoleAnnotation(AdminSupportConsoleController.class.getMethod(
            "getProblemTrace", Jwt.class, org.springframework.security.core.Authentication.class, UUID.class, String.class, String.class
        ));
        assertAdminRoleAnnotation(AdminSupportConsoleController.class.getMethod("listAuditLogs", int.class));

        // 2. Fail-closed 503 checks using standalone MockMvc with AdminSupportProperties.defaults()
        UUID standaloneAdminId = UUID.fromString("00000000-0000-0000-0000-00000000DD01");
        AdminSupportMetrics disabledMetrics = new AdminSupportMetrics(new SimpleMeterRegistry());
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneOffset.UTC);
        AdminSupportProperties defaults = AdminSupportProperties.defaults();
        AdminSupportQueryService disabledQuery = new AdminSupportQueryService(
            readRepository, writeRepository, defaults, disabledMetrics, fixedClock
        );
        AdminRawContentAccessService disabledRaw = new AdminRawContentAccessService(
            readRepository, writeRepository, defaults, disabledMetrics, fixedClock
        );
        WrongAnswerReportService disabledWar = new WrongAnswerReportService(
            readRepository, writeRepository, defaults, disabledMetrics, fixedClock
        );
        AdminRemediationService disabledRem = new AdminRemediationService(
            appleBillingApplicationService,
            entitlementApplicationService,
            quotaLedgerService,
            featureFlagGovernanceService,
            readRepository,
            writeRepository,
            defaults,
            disabledMetrics,
            fixedClock
        );
        MockMvc disabledAdminMvc = MockMvcBuilders
            .standaloneSetup(new AdminSupportConsoleController(disabledQuery, disabledRaw, disabledWar, disabledRem))
            .setControllerAdvice(new ApiExceptionHandler())
            .setCustomArgumentResolvers(new TestJwtResolver(standaloneAdminId))
            .build();

        disabledAdminMvc.perform(get("/api/v1/admin/support/audit"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("ADMIN_CONSOLE_DISABLED"));

        disabledAdminMvc.perform(post("/api/v1/admin/support/remediations/quota-sync")
                .header("Idempotency-Key", "idem-disabled-rem-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(
                    new QuotaSyncRemediationRequest(standaloneAdminId, "Check disabled state", false)
                )))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("ADMIN_REMEDIATION_DISABLED"));

        disabledAdminMvc.perform(post("/api/v1/admin/support/problems/" + UUID.randomUUID() + "/raw-content-access")
                .header("Idempotency-Key", "idem-disabled-raw-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(
                    new RawContentGrantRequest("SUP-108", "Inspect disabled raw access", 15, false)
                )))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("ADMIN_RAW_CONTENT_ACCESS_DISABLED"));

        // 3. Unauthenticated -> 401
        HttpResponse<String> unauth = httpClient.send(
            HttpRequest.newBuilder(uri("/api/v1/admin/support/audit")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(unauth.statusCode()).isEqualTo(401);

        // Sign in learner A, learner B, plain ROLE_ADMIN, and elevated ROLE_ADMIN + ADMIN_RAW_CONTENT_READ
        AuthSessionResult learnerA = signIn("apple-sub-learner-a");
        AuthSessionResult learnerB = signIn("apple-sub-learner-b");
        AuthSessionResult plainAdmin = signIn("apple-sub-plain-admin");
        AuthSessionResult elevatedAdmin = signIn("apple-sub-elevated-admin");

        String plainAdminJwt = issueJwt(plainAdmin, List.of("ADMIN"), List.of());
        String elevatedAdminJwt = issueJwt(elevatedAdmin, List.of("ADMIN"), List.of("ADMIN_RAW_CONTENT_READ"));

        // 4. Regular learner JWT on admin endpoints -> 403 ADMIN_SUPPORT_FORBIDDEN + application/problem+json
        HttpResponse<String> learnerSupportDenied = getWithAuth("/api/v1/admin/support/audit", learnerA.accessToken());
        assertThat(learnerSupportDenied.statusCode()).isEqualTo(403);
        assertThat(learnerSupportDenied.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse(""))
            .contains(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(OBJECT_MAPPER.readTree(learnerSupportDenied.body()).get("code").asText())
            .isEqualTo("ADMIN_SUPPORT_FORBIDDEN");

        HttpResponse<String> learnerAiOpsDenied = getWithAuth("/api/v1/admin/ai-ops/overview", learnerA.accessToken());
        assertThat(learnerAiOpsDenied.statusCode()).isEqualTo(403);
        assertThat(OBJECT_MAPPER.readTree(learnerAiOpsDenied.body()).get("code").asText())
            .isEqualTo("ADMIN_SUPPORT_FORBIDDEN");

        // Seed problem_session + asset + parse + ai_usage_record owned by learnerA
        UUID problemSessionId = UUID.fromString("00000000-0000-0000-0000-00000000E101");
        String secretObjectKey = "users/" + learnerA.userId() + "/raw-secret-image-key.jpg";
        String secretRawJson = "{\"rawText\":\"SECRET_STUDENT_OCR_TEXT\"}";
        String secretNormalizedJson = "{\"prompt\":\"SECRET_NORMALIZED_EQUATION\"}";
        seedProblemTraceFixtures(learnerA.userId(), problemSessionId, secretObjectKey, secretRawJson, secretNormalizedJson);

        // 5. BOLA check: learner B reporting learner A's session -> 404 PROBLEM_SESSION_NOT_FOUND
        String reportPayload = """
            {"problemSessionId":"%s","reportReason":"WRONG_FINAL_ANSWER","userNote":"Sign looks wrong","trainingEligible":false}
            """.formatted(problemSessionId);
        HttpResponse<String> bolaDenied = sendJson(
            "POST",
            "/api/v1/support/wrong-answer-reports",
            reportPayload,
            learnerB.accessToken(),
            "war-bola-check-1"
        );
        assertThat(bolaDenied.statusCode()).isEqualTo(404);
        assertThat(OBJECT_MAPPER.readTree(bolaDenied.body()).get("code").asText())
            .isEqualTo("PROBLEM_SESSION_NOT_FOUND");

        // Learner A reporting owned session -> 201 Created
        HttpResponse<String> ownedReport = sendJson(
            "POST",
            "/api/v1/support/wrong-answer-reports",
            reportPayload,
            learnerA.accessToken(),
            "war-owned-check-1"
        );
        assertThat(ownedReport.statusCode()).isEqualTo(201);
        assertThat(ownedReport.headers().firstValue(HttpHeaders.CACHE_CONTROL).orElse(""))
            .contains("no-store")
            .contains("private");

        // 6. Plain ROLE_ADMIN lookup, trace (by sessionId and traceId), billing, AI ops, and audit -> 200 OK & ZERO raw leakage
        HttpResponse<String> lookupRes = getWithAuth(
            "/api/v1/admin/support/users/lookup?type=USER_ID&value=" + learnerA.userId(),
            plainAdminJwt
        );
        assertThat(lookupRes.statusCode()).isEqualTo(200);
        assertNoStorePrivate(lookupRes);
        JsonNode lookupJson = OBJECT_MAPPER.readTree(lookupRes.body());
        assertThat(lookupJson.get("rawContentRedacted").asBoolean()).isTrue();
        assertThat(lookupJson.get("llmTokensUsed").asInt()).isZero();
        assertThat(lookupJson.get("trainingEligible").asBoolean()).isFalse();

        HttpResponse<String> traceBySession = getWithAuth(
            "/api/v1/admin/support/problems/trace?problemSessionId=" + problemSessionId,
            plainAdminJwt
        );
        assertThat(traceBySession.statusCode()).isEqualTo(200);
        assertNoStorePrivate(traceBySession);
        assertThat(traceBySession.body())
            .doesNotContain(secretObjectKey)
            .doesNotContain("SECRET_STUDENT_OCR_TEXT")
            .doesNotContain("SECRET_NORMALIZED_EQUATION")
            .doesNotContain("objectKey")
            .doesNotContain("rawOutputJsonb")
            .doesNotContain("normalizedProblemJsonb");
        assertThat(OBJECT_MAPPER.readTree(traceBySession.body()).get("rawContentRedacted").asBoolean()).isTrue();

        HttpResponse<String> traceByTraceId = getWithAuth(
            "/api/v1/admin/support/problems/trace?traceId=trace-108-sec-1",
            plainAdminJwt
        );
        assertThat(traceByTraceId.statusCode()).isEqualTo(200);
        assertThat(traceByTraceId.body()).doesNotContain("SECRET_STUDENT_OCR_TEXT");

        HttpResponse<String> billingRes = getWithAuth(
            "/api/v1/admin/support/users/" + learnerA.userId() + "/billing",
            plainAdminJwt
        );
        assertThat(billingRes.statusCode()).isEqualTo(200);
        assertNoStorePrivate(billingRes);

        HttpResponse<String> aiOpsRes = getWithAuth(
            "/api/v1/admin/ai-ops/overview",
            plainAdminJwt
        );
        assertThat(aiOpsRes.statusCode()).isEqualTo(200);
        assertNoStorePrivate(aiOpsRes);

        // 7. Plain ROLE_ADMIN without ADMIN_RAW_CONTENT_READ -> 403 ADMIN_RAW_CONTENT_FORBIDDEN
        String grantBody = """
            {"ticketReference":"SUP-SEC-108","reason":"Need raw parse inspection for ticket SUP-SEC-108","ttlMinutes":15,"trainingEligible":false}
            """;
        HttpResponse<String> plainGrantDenied = sendJson(
            "POST",
            "/api/v1/admin/support/problems/" + problemSessionId + "/raw-content-access",
            grantBody,
            plainAdminJwt,
            "grant-plain-denied-1"
        );
        assertThat(plainGrantDenied.statusCode()).isEqualTo(403);
        assertThat(OBJECT_MAPPER.readTree(plainGrantDenied.body()).get("code").asText())
            .isEqualTo("ADMIN_RAW_CONTENT_FORBIDDEN");

        // 8. Elevated admin creates grant -> inspects raw content -> expired grant rejected -> audit logged
        HttpResponse<String> grantCreated = sendJson(
            "POST",
            "/api/v1/admin/support/problems/" + problemSessionId + "/raw-content-access",
            grantBody,
            elevatedAdminJwt,
            "grant-elevated-ok-1"
        );
        assertThat(grantCreated.statusCode()).isEqualTo(201);
        assertNoStorePrivate(grantCreated);
        UUID grantId = UUID.fromString(OBJECT_MAPPER.readTree(grantCreated.body()).get("grantId").asText());

        HttpResponse<String> inspected = getWithAuth(
            "/api/v1/admin/support/problems/" + problemSessionId + "/raw-content?grantId=" + grantId,
            elevatedAdminJwt
        );
        assertThat(inspected.statusCode()).isEqualTo(200);
        assertNoStorePrivate(inspected);
        assertThat(inspected.body())
            .contains(secretObjectKey)
            .contains("SECRET_STUDENT_OCR_TEXT")
            .contains("SECRET_NORMALIZED_EQUATION");

        // Force grant expiry and verify 409 ADMIN_RAW_CONTENT_GRANT_EXPIRED
        jdbcTemplate.update(
            "UPDATE admin_raw_content_access_grants SET granted_at = NOW() - INTERVAL '30 minutes', expires_at = NOW() - INTERVAL '5 minutes' WHERE id = ?",
            grantId
        );
        HttpResponse<String> expiredRead = getWithAuth(
            "/api/v1/admin/support/problems/" + problemSessionId + "/raw-content?grantId=" + grantId,
            elevatedAdminJwt
        );
        assertThat(expiredRead.statusCode()).isEqualTo(409);
        assertThat(OBJECT_MAPPER.readTree(expiredRead.body()).get("code").asText())
            .isEqualTo("ADMIN_RAW_CONTENT_GRANT_EXPIRED");

        // Verify audit trail contains both RAW_CONTENT_GRANT_CREATED and RAW_CONTENT_INSPECTED
        HttpResponse<String> auditRes = getWithAuth("/api/v1/admin/support/audit?limit=50", plainAdminJwt);
        assertThat(auditRes.statusCode()).isEqualTo(200);
        assertNoStorePrivate(auditRes);
        assertThat(auditRes.body())
            .contains("RAW_CONTENT_GRANT_CREATED")
            .contains("RAW_CONTENT_INSPECTED")
            .contains("USER_LOOKUP_READ")
            .contains("PROBLEM_TRACE_READ");
    }

    private void seedProblemTraceFixtures(
        UUID userId,
        UUID problemSessionId,
        String objectKey,
        String rawOutputJson,
        String normalizedProblemJson
    ) {
        Timestamp now = Timestamp.from(Instant.now());
        Timestamp expires = Timestamp.from(Instant.now().plusSeconds(900));
        String hex64 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        UUID assetId = UUID.randomUUID();
        UUID derivativeId = UUID.randomUUID();
        UUID recJobId = UUID.randomUUID();
        UUID recEvidenceId = UUID.randomUUID();
        UUID parseJobId = UUID.randomUUID();
        UUID parseId = UUID.randomUUID();

        jdbcTemplate.update(
            "INSERT INTO problem_sessions (id, user_id, status, input_mode, created_at, updated_at, version) "
                + "VALUES (?, ?, 'CREATED', 'CAMERA', ?, ?, 0)",
            problemSessionId,
            userId,
            now,
            now
        );
        jdbcTemplate.update(
            "INSERT INTO problem_assets (id, problem_session_id, user_id, source_type, asset_kind, status, object_key, content_type, size_bytes, checksum_algorithm, checksum_value, crop_x, crop_y, crop_width, crop_height, image_width, image_height, retention_class, upload_expires_at, available_at, created_at, updated_at, version, reservation_idempotency_key, reservation_request_hash) "
                + "VALUES (?, ?, ?, 'CAMERA', 'IMAGE', 'AVAILABLE', ?, 'image/jpeg', 1024, 'SHA-256', ?, 0, 0, 1, 1, 1200, 1600, 'TEMPORARY_RAW', ?, ?, ?, ?, 0, 'res-108-sec-1', ?)",
            assetId,
            problemSessionId,
            userId,
            objectKey,
            hex64,
            expires,
            now,
            now,
            now,
            hex64
        );
        jdbcTemplate.update(
            "INSERT INTO problem_asset_derivatives (id, source_asset_id, problem_session_id, user_id, derivative_kind, status, selected_for_recognition, object_key, content_type, size_bytes, checksum_algorithm, checksum_value, width, height, source_width, source_height, crop_x, crop_y, crop_width, crop_height, processor_name, processor_version, configuration_version, orientation_normalized, perspective_applied, contrast_normalized, resized, quality_outcome, created_at, updated_at, completed_at, version) "
                + "VALUES (?, ?, ?, ?, 'OCR_OPTIMIZED', 'READY', TRUE, ?, 'image/jpeg', 512, 'SHA-256', ?, 1200, 1600, 1200, 1600, 0, 0, 1, 1, 'DOCUMENT_PREPROCESSOR', '1.0', 'capture-quality-v1', TRUE, FALSE, FALSE, FALSE, 'PASS', ?, ?, ?, 0)",
            derivativeId,
            assetId,
            problemSessionId,
            userId,
            "users/" + userId + "/deriv-108.jpg",
            hex64,
            now,
            now,
            now
        );
        jdbcTemplate.update(
            "INSERT INTO recognition_jobs (id, user_id, problem_session_id, source_asset_id, input_derivative_id, status, capability, prompt_id, prompt_version, schema_version, route_policy_version, attempt_count, max_attempts, next_attempt_at, created_at, updated_at, completed_at, version) "
                + "VALUES (?, ?, ?, ?, ?, 'SUCCEEDED', 'VISION_PARSE', 'vision_parse', 'v1', 'recognition-v1', 'route-v1', 1, 3, ?, ?, ?, ?, 0)",
            recJobId,
            userId,
            problemSessionId,
            assetId,
            derivativeId,
            now,
            now,
            now,
            now
        );
        jdbcTemplate.update(
            "INSERT INTO recognition_evidence (id, recognition_job_id, user_id, problem_session_id, source_asset_id, input_derivative_id, revision, capability, schema_version, raw_output_jsonb, normalized_evidence_jsonb, upstream_quality_evidence_jsonb, provider, model, route_policy_version, prompt_id, prompt_version, request_units, provider_latency_ms, total_latency_ms, estimated_cost_micros, currency, pricing_version, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, 1, 'VISION_PARSE', 'recognition-v1', '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, 'gemini', 'gemini-2.5-flash', 'route-v1', 'vision_parse', 'v1', 1, 100, 120, 500, 'USD', 'v1', ?)",
            recEvidenceId,
            recJobId,
            userId,
            problemSessionId,
            assetId,
            derivativeId,
            now
        );
        jdbcTemplate.update(
            "INSERT INTO problem_parse_jobs (id, user_id, problem_session_id, recognition_evidence_id, recognition_evidence_revision, status, capability, prompt_id, prompt_version, schema_version, route_policy_version, attempt_count, max_attempts, next_attempt_at, created_at, updated_at, completed_at, version) "
                + "VALUES (?, ?, ?, ?, 1, 'SUCCEEDED', 'PROBLEM_NORMALIZE', 'problem_normalize', 'v1', 'problem-parse-v1', 'route-v1', 1, 3, ?, ?, ?, ?, 0)",
            parseJobId,
            userId,
            problemSessionId,
            recEvidenceId,
            now,
            now,
            now,
            now
        );
        jdbcTemplate.update(
            "INSERT INTO problem_parses (id, parse_job_id, user_id, problem_session_id, recognition_evidence_id, recognition_evidence_revision, revision, source, support_status, schema_version, raw_output_jsonb, normalized_problem_jsonb, provider, model, route_policy_version, prompt_id, prompt_version, fallback_used, request_units, provider_latency_ms, total_latency_ms, estimated_cost_micros, currency, pricing_version, created_at) "
                + "VALUES (?, ?, ?, ?, ?, 1, 1, 'AI', 'SUPPORTED', 'problem-parse-v1', CAST(? AS jsonb), CAST(? AS jsonb), 'gemini', 'gemini-2.5-flash', 'route-v1', 'problem_normalize', 'v1', FALSE, 1, 120, 150, 600, 'USD', 'v1', ?)",
            parseId,
            parseJobId,
            userId,
            problemSessionId,
            recEvidenceId,
            rawOutputJson,
            normalizedProblemJson,
            now
        );
        jdbcTemplate.update(
            "INSERT INTO ai_usage_records (id, operation_id, user_id, problem_session_id, capability, route_policy_version, route_id, provider, model, prompt_id, prompt_version, schema_version, status, pricing_version, currency, correlation_id, trace_id, estimated_cost_micros, provider_latency_ms, gateway_latency_ms, created_at) "
                + "VALUES (?, ?, ?, ?, 'SOLVE', 'route-v2', 'economy-primary', 'gemini', 'gemini-2.5-flash', 'primary_solve', 'v2', 'solution-candidate-v1', 'SUCCEEDED', 'pricing-v1', 'USD', 'corr-108-sec', 'trace-108-sec-1', 1200, 310, 350, ?)",
            UUID.randomUUID(),
            UUID.randomUUID(),
            userId,
            problemSessionId,
            now
        );
    }

    private static void assertAdminRoleAnnotation(java.lang.reflect.Method method) {
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertThat(preAuthorize).as("Missing @PreAuthorize on " + method.getName()).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasRole('ADMIN')");
    }

    private static void assertNoStorePrivate(HttpResponse<String> response) {
        assertThat(response.headers().firstValue(HttpHeaders.CACHE_CONTROL).orElse(""))
            .contains("no-store")
            .contains("private");
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> getWithAuth(String path, String bearerToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
            .GET()
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
            .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> sendJson(
        String method,
        String path,
        String bodyJson,
        String bearerToken,
        String idempotencyKey
    ) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
            .method(method, HttpRequest.BodyPublishers.ofString(bodyJson))
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private AuthSessionResult signIn(String subject) {
        return identityApplicationService.signInWithApple(new AppleSignInCommand(subject, "unused-code", "nonce"));
    }

    private String issueJwt(AuthSessionResult session, List<String> roles, List<String> authorities) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
            .issuer("verified-ai-test")
            .subject(session.userId().toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(3600))
            .claim("sid", session.sessionId().toString())
            .claim("typ", "access")
            .claim("roles", roles);
        if (!authorities.isEmpty()) {
            claims.claim("authorities", authorities);
        }
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    private static final class TestJwtResolver implements HandlerMethodArgumentResolver {
        private final UUID userId;

        TestJwtResolver(UUID userId) {
            this.userId = userId;
        }

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                && Jwt.class.isAssignableFrom(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
        ) {
            return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(userId.toString())
                .claim("sid", UUID.nameUUIDFromBytes(("standalone-session-" + userId).getBytes()).toString())
                .build();
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
