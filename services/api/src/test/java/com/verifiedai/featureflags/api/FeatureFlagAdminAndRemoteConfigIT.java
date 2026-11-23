package com.verifiedai.featureflags.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.verifiedai.featureflags.application.FeatureFlagEvaluationService;
import com.verifiedai.featureflags.application.FeatureFlagGovernanceService;
import com.verifiedai.featureflags.application.FeatureFlagMetrics;
import com.verifiedai.featureflags.infrastructure.FeatureFlagJdbcRepository;
import com.verifiedai.featureflags.infrastructure.FeatureFlagProperties;
import com.verifiedai.identity.application.AppleSignInCommand;
import com.verifiedai.identity.application.AuthSessionResult;
import com.verifiedai.identity.application.IdentityApplicationService;
import com.verifiedai.identity.domain.model.AppleIdentityVerifier;
import com.verifiedai.identity.domain.model.VerifiedAppleIdentity;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.sharedkernel.error.ApiExceptionHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
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

@Import(FeatureFlagAdminAndRemoteConfigIT.AuthTestConfiguration.class)
@TestPropertySource(properties = {
    "verifiedai.featureflags.enabled=true",
    "verifiedai.featureflags.client-config-enabled=true",
    "verifiedai.featureflags.admin-mutations-enabled=true"
})
final class FeatureFlagAdminAndRemoteConfigIT extends PostgresIntegrationTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();

    @Value("${local.server.port}")
    int port;

    @Autowired
    IdentityApplicationService identityApplicationService;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    FeatureFlagJdbcRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void resetTables() {
        jdbcTemplate.execute("""
            truncate table
                feature_flag_audit_logs,
                experiment_assignments,
                refresh_tokens,
                sessions,
                user_identities,
                users
            cascade
            """);
        jdbcTemplate.execute("""
            UPDATE feature_flag_definitions
            SET enabled = CASE WHEN flag_key IN ('pdf_upload_enabled', 'strong_model_pct') THEN TRUE ELSE FALSE END,
                kill_switch_active = FALSE,
                rollout_basis_points = CASE WHEN flag_key IN ('pdf_upload_enabled', 'strong_model_pct') THEN 10000 ELSE 0 END,
                current_value_json = default_value_json,
                version = 0,
                updated_at = NOW()
            """);
        jdbcTemplate.execute("""
            UPDATE experiment_definitions
            SET status = 'DRAFT',
                rollout_basis_points = 0,
                experiment_version = 1,
                version = 0,
                updated_at = NOW()
            """);
    }

    @Test
    void enforcesAuthnAdminAuthzFailClosedDefaultsEtagAndFullLifecycle() throws Exception {
        // Verify @PreAuthorize("hasRole('ADMIN')") on all admin controller handler methods
        assertAdminRoleAnnotation(FeatureFlagAdminController.class.getMethod("listFeatureFlags"));
        assertAdminRoleAnnotation(FeatureFlagAdminController.class.getMethod(
            "updateFlag", Jwt.class, String.class, String.class,
            com.verifiedai.featureflags.api.dto.FeatureFlagUpdateRequest.class
        ));
        assertAdminRoleAnnotation(FeatureFlagAdminController.class.getMethod(
            "toggleKillSwitch", Jwt.class, String.class, String.class,
            com.verifiedai.featureflags.api.dto.FeatureFlagKillSwitchRequest.class
        ));
        assertAdminRoleAnnotation(FeatureFlagAdminController.class.getMethod("listExperiments"));
        assertAdminRoleAnnotation(FeatureFlagAdminController.class.getMethod(
            "updateExperiment", Jwt.class, String.class, String.class,
            com.verifiedai.featureflags.api.dto.ExperimentUpdateRequest.class
        ));
        assertAdminRoleAnnotation(FeatureFlagAdminController.class.getMethod("listAuditLogs", int.class));

        // Fail-closed 503 FEATURE_FLAG_DISABLED when properties are false
        UUID standaloneUserId = UUID.fromString("00000000-0000-0000-0000-00000000CC01");
        FeatureFlagMetrics disabledMetrics = new FeatureFlagMetrics(new SimpleMeterRegistry());
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneOffset.UTC);
        FeatureFlagEvaluationService disabledEval = new FeatureFlagEvaluationService(
            repository,
            FeatureFlagProperties.defaults(),
            disabledMetrics,
            fixedClock
        );
        FeatureFlagGovernanceService disabledGov = new FeatureFlagGovernanceService(
            repository,
            FeatureFlagProperties.defaults(),
            disabledMetrics,
            fixedClock
        );
        MockMvc disabledConfigMvc = MockMvcBuilders
            .standaloneSetup(new RemoteConfigController(disabledEval))
            .setControllerAdvice(new ApiExceptionHandler())
            .setCustomArgumentResolvers(new TestJwtResolver(standaloneUserId))
            .build();
        MockMvc disabledAdminMvc = MockMvcBuilders
            .standaloneSetup(new FeatureFlagAdminController(disabledGov))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

        for (String prefix : new String[]{"/api/v1", "/v1"}) {
            disabledConfigMvc.perform(get(prefix + "/config/remote"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FEATURE_FLAG_DISABLED"));
            disabledAdminMvc.perform(get(prefix + "/admin/feature-flags"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FEATURE_FLAG_DISABLED"));
        }

        // Unauthenticated requests fail with 401 AUTH_TOKEN_EXPIRED on /v1 & /api/v1 mirrors
        for (String path : new String[]{
            "/api/v1/config/remote",
            "/v1/config/remote",
            "/api/v1/admin/feature-flags",
            "/v1/admin/feature-flags",
            "/api/v1/admin/feature-flags/audit",
            "/v1/admin/feature-flags/audit"
        }) {
            HttpResponse<String> unauth = sendGet(path, null, null);
            assertThat(unauth.statusCode()).isEqualTo(401);
            assertThat(unauth.body()).contains("\"code\":\"AUTH_TOKEN_EXPIRED\"");
        }

        // Regular learner JWT receives 200 OK with strictly client_safe=true flags & strong ETag + Cache-Control
        AuthSessionResult learner = signIn("ff-regular-learner");
        String firstEtag = null;
        for (String prefix : new String[]{"/api/v1", "/v1"}) {
            HttpResponse<String> remoteResp = sendGet(prefix + "/config/remote", learner.accessToken(), null);
            assertThat(remoteResp.statusCode()).isEqualTo(200);
            String cacheControl = remoteResp.headers().firstValue(HttpHeaders.CACHE_CONTROL).orElse("");
            assertThat(cacheControl).contains("no-store").contains("private");
            String etag = remoteResp.headers().firstValue(HttpHeaders.ETAG).orElse("");
            assertThat(etag).startsWith("\"").endsWith("\"");
            if (firstEtag == null) {
                firstEtag = etag;
            } else {
                assertThat(etag).isEqualTo(firstEtag);
            }

            JsonNode body = OBJECT_MAPPER.readTree(remoteResp.body());
            assertThat(body.path("schemaVersion").asText()).isEqualTo("remote-config-v1");
            assertThat(body.path("llmTokensUsed").asInt()).isZero();
            assertThat(body.path("trainingEligible").asBoolean()).isFalse();

            List<String> flagKeys = new ArrayList<>();
            for (JsonNode flagNode : body.path("flags")) {
                flagKeys.add(flagNode.path("flagKey").asText());
            }
            assertThat(flagKeys)
                .containsExactlyInAnyOrder(
                    "client_mistake_moment_paywall_ui",
                    "client_platform_health_screen",
                    "pdf_upload_enabled"
                )
                .doesNotContain(
                    "secondary_solver_enabled",
                    "advanced_verification_enabled",
                    "tutor_v2_enabled",
                    "adaptive_plan_v2_enabled",
                    "verification_method_false_positive_kill_switch",
                    "strong_model_pct",
                    "proprietary_canary_pct",
                    "daily_solve_quota_override"
                );

            List<String> expKeys = new ArrayList<>();
            for (JsonNode expNode : body.path("experiments")) {
                expKeys.add(expNode.path("experimentKey").asText());
            }
            assertThat(expKeys)
                .containsExactlyInAnyOrder("EXP-LOS-01", "EXP-LOS-08", "EXP-LOS-09")
                .doesNotContain("EXP-LOS-02");
        }

        // Conditional GET with If-None-Match -> 304 Not Modified
        HttpResponse<String> notModified = sendGet("/api/v1/config/remote", learner.accessToken(), firstEtag);
        assertThat(notModified.statusCode()).isEqualTo(304);
        assertThat(notModified.body()).isEmpty();
        assertThat(notModified.headers().firstValue(HttpHeaders.ETAG)).contains(firstEtag);
        assertThat(notModified.headers().firstValue(HttpHeaders.CACHE_CONTROL).orElse(""))
            .contains("no-store").contains("private");

        // Regular learner JWT rejected on all admin routes (403 FEATURE_FLAG_ADMIN_FORBIDDEN)
        for (String adminGet : new String[]{
            "/api/v1/admin/feature-flags",
            "/v1/admin/feature-flags",
            "/api/v1/admin/feature-flags/experiments",
            "/v1/admin/feature-flags/audit"
        }) {
            HttpResponse<String> forbidden = sendGet(adminGet, learner.accessToken(), null);
            assertThat(forbidden.statusCode()).isEqualTo(403);
            assertThat(forbidden.body()).contains("\"code\":\"FEATURE_FLAG_ADMIN_FORBIDDEN\"");
        }

        HttpResponse<String> forbiddenPost = sendMutation(
            "POST",
            "/api/v1/admin/feature-flags/pdf_upload_enabled/kill-switch",
            learner.accessToken(),
            "idem-learner-forbidden-1",
            "{\"active\":true,\"reason\":\"Unauthorized learner kill switch attempt\",\"expectedVersion\":0,\"trainingEligible\":false}"
        );
        assertThat(forbiddenPost.statusCode()).isEqualTo(403);
        assertThat(forbiddenPost.body()).contains("\"code\":\"FEATURE_FLAG_ADMIN_FORBIDDEN\"");

        // ROLE_ADMIN lists flags & experiments
        AuthSessionResult admin = signIn("ff-platform-admin");
        String adminToken = issueAdminJwt(admin);

        HttpResponse<String> listResp = sendGet("/api/v1/admin/feature-flags", adminToken, null);
        assertThat(listResp.statusCode()).isEqualTo(200);
        assertThat(listResp.headers().firstValue(HttpHeaders.CACHE_CONTROL).orElse(""))
            .contains("no-store").contains("private");
        JsonNode listJson = OBJECT_MAPPER.readTree(listResp.body());
        assertThat(listJson.path("schemaVersion").asText()).isEqualTo("feature-flag-list-v1");
        assertThat(listJson.path("flagCount").asInt()).isEqualTo(11);
        assertThat(listJson.path("experimentCount").asInt()).isEqualTo(4);
        assertThat(listJson.path("llmTokensUsed").asInt()).isZero();
        assertThat(listJson.path("trainingEligible").asBoolean()).isFalse();

        long healthVersion = findVersion(listJson.path("flags"), "flagKey", "client_platform_health_screen");
        long pdfVersion = findVersion(listJson.path("flags"), "flagKey", "pdf_upload_enabled");
        long exp01Version = findVersion(listJson.path("experiments"), "experimentKey", "EXP-LOS-01");

        // Admin enables client_platform_health_screen -> learner ETag invalidates & sees enabled=true
        HttpResponse<String> updateFlagResp = sendMutation(
            "PATCH",
            "/api/v1/admin/feature-flags/client_platform_health_screen",
            adminToken,
            "idem-http-health-1",
            """
            {
              "enabled": true,
              "rolloutBasisPoints": 10000,
              "currentValueJson": "true",
              "reason": "Enable platform health screen for beta",
              "expectedVersion": %d,
              "trainingEligible": false
            }
            """.formatted(healthVersion)
        );
        assertThat(updateFlagResp.statusCode()).isEqualTo(200);

        HttpResponse<String> refreshedRemote = sendGet("/api/v1/config/remote", learner.accessToken(), firstEtag);
        assertThat(refreshedRemote.statusCode()).isEqualTo(200);
        String newEtag = refreshedRemote.headers().firstValue(HttpHeaders.ETAG).orElse("");
        assertThat(newEtag).isNotEqualTo(firstEtag);
        JsonNode refreshedJson = OBJECT_MAPPER.readTree(refreshedRemote.body());
        assertThat(findFlagEnabled(refreshedJson.path("flags"), "client_platform_health_screen")).isTrue();

        // Admin toggles PDF upload kill switch -> learner immediately receives killSwitches=["pdf_upload_enabled"]
        HttpResponse<String> killPdfResp = sendMutation(
            "POST",
            "/api/v1/admin/feature-flags/pdf_upload_enabled/kill-switch",
            adminToken,
            "idem-http-kill-pdf-1",
            """
            {
              "active": true,
              "reason": "Mitigate malicious PDF parser spike",
              "expectedVersion": %d,
              "trainingEligible": false
            }
            """.formatted(pdfVersion)
        );
        assertThat(killPdfResp.statusCode()).isEqualTo(200);

        HttpResponse<String> postKillRemote = sendGet("/v1/config/remote", learner.accessToken(), null);
        assertThat(postKillRemote.statusCode()).isEqualTo(200);
        JsonNode postKillJson = OBJECT_MAPPER.readTree(postKillRemote.body());
        assertThat(postKillJson.path("killSwitches")).extracting(JsonNode::asText).containsExactly("pdf_upload_enabled");
        assertThat(findFlagEnabled(postKillJson.path("flags"), "pdf_upload_enabled")).isFalse();

        // Admin transitions EXP-LOS-01: DRAFT -> RUNNING -> KILLED -> illegal KILLED->RUNNING (409) -> CONCLUDED
        HttpResponse<String> expRunResp = sendMutation(
            "PATCH",
            "/api/v1/admin/feature-flags/experiments/EXP-LOS-01",
            adminToken,
            "idem-http-exp-run-1",
            """
            {
              "status": "RUNNING",
              "rolloutBasisPoints": 5000,
              "reason": "Start proof card test at 50 percent",
              "expectedVersion": %d,
              "trainingEligible": false
            }
            """.formatted(exp01Version)
        );
        assertThat(expRunResp.statusCode()).isEqualTo(200);
        exp01Version = findVersion(OBJECT_MAPPER.readTree(expRunResp.body()).path("experiments"), "experimentKey", "EXP-LOS-01");

        HttpResponse<String> expKillResp = sendMutation(
            "PATCH",
            "/api/v1/admin/feature-flags/experiments/EXP-LOS-01",
            adminToken,
            "idem-http-exp-kill-1",
            """
            {
              "status": "KILLED",
              "rolloutBasisPoints": 0,
              "reason": "Kill experiment after telemetry anomaly",
              "expectedVersion": %d,
              "trainingEligible": false
            }
            """.formatted(exp01Version)
        );
        assertThat(expKillResp.statusCode()).isEqualTo(200);
        exp01Version = findVersion(OBJECT_MAPPER.readTree(expKillResp.body()).path("experiments"), "experimentKey", "EXP-LOS-01");

        HttpResponse<String> illegalReviveResp = sendMutation(
            "PATCH",
            "/api/v1/admin/feature-flags/experiments/EXP-LOS-01",
            adminToken,
            "idem-http-exp-illegal-1",
            """
            {
              "status": "RUNNING",
              "rolloutBasisPoints": 5000,
              "reason": "Illegal direct resume from KILLED",
              "expectedVersion": %d,
              "trainingEligible": false
            }
            """.formatted(exp01Version)
        );
        assertThat(illegalReviveResp.statusCode()).isEqualTo(409);
        assertThat(illegalReviveResp.body()).contains("\"code\":\"FEATURE_FLAG_INVALID_TRANSITION\"");

        HttpResponse<String> concludeResp = sendMutation(
            "PATCH",
            "/api/v1/admin/feature-flags/experiments/EXP-LOS-01",
            adminToken,
            "idem-http-exp-conclude-1",
            """
            {
              "status": "CONCLUDED",
              "rolloutBasisPoints": 0,
              "reason": "Conclude experiment permanently",
              "expectedVersion": %d,
              "trainingEligible": false
            }
            """.formatted(exp01Version)
        );
        assertThat(concludeResp.statusCode()).isEqualTo(200);

        // Audit endpoint returns immutable ledger with actorRole=ROLE_ADMIN and trainingEligible=false
        HttpResponse<String> auditResp = sendGet("/api/v1/admin/feature-flags/audit?limit=20", adminToken, null);
        assertThat(auditResp.statusCode()).isEqualTo(200);
        JsonNode auditJson = OBJECT_MAPPER.readTree(auditResp.body());
        assertThat(auditJson.path("schemaVersion").asText()).isEqualTo("feature-flag-audit-list-v1");
        assertThat(auditJson.path("entryCount").asInt()).isEqualTo(5);
        assertThat(auditJson.path("llmTokensUsed").asInt()).isZero();
        assertThat(auditJson.path("trainingEligible").asBoolean()).isFalse();
        for (JsonNode entry : auditJson.path("entries")) {
            assertThat(entry.path("actorRole").asText()).isEqualTo("ROLE_ADMIN");
            assertThat(entry.path("trainingEligible").asBoolean()).isFalse();
            assertThat(entry.path("beforeStateJson").asText()).startsWith("{");
            assertThat(entry.path("afterStateJson").asText()).startsWith("{");
        }
    }

    private static void assertAdminRoleAnnotation(Method method) {
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasRole('ADMIN')");
    }

    private static long findVersion(JsonNode arrayNode, String keyField, String targetKey) {
        for (JsonNode item : arrayNode) {
            if (targetKey.equals(item.path(keyField).asText())) {
                return item.path("version").asLong();
            }
        }
        throw new AssertionError("Missing key " + targetKey);
    }

    private static boolean findFlagEnabled(JsonNode flagsArray, String flagKey) {
        for (JsonNode item : flagsArray) {
            if (flagKey.equals(item.path("flagKey").asText())) {
                return item.path("enabled").asBoolean();
            }
        }
        throw new AssertionError("Missing flag " + flagKey);
    }

    private HttpResponse<String> sendGet(String path, String bearerToken, String ifNoneMatch) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .GET();
        if (bearerToken != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        }
        if (ifNoneMatch != null) {
            builder.header(HttpHeaders.IF_NONE_MATCH, ifNoneMatch);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> sendMutation(
        String method,
        String path,
        String bearerToken,
        String idempotencyKey,
        String bodyJson
    ) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
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

    private String issueAdminJwt(AuthSessionResult session) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer("verified-ai-test")
            .subject(session.userId().toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(3600))
            .claim("sid", session.sessionId().toString())
            .claim("typ", "access")
            .claim("roles", List.of("ADMIN"))
            .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
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
