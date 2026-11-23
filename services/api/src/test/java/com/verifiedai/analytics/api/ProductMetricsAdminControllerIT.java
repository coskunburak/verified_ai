package com.verifiedai.analytics.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.verifiedai.analytics.application.AnalyticsEventPort;
import com.verifiedai.analytics.application.AnalyticsMetrics;
import com.verifiedai.analytics.application.ProductMetricsQueryService;
import com.verifiedai.analytics.application.RecordAnalyticsEventCommand;
import com.verifiedai.analytics.infrastructure.AnalyticsProperties;
import com.verifiedai.analytics.infrastructure.ProductMetricsJdbcRepository;
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
import java.util.List;
import java.util.Map;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@Import(ProductMetricsAdminControllerIT.AuthTestConfiguration.class)
@TestPropertySource(properties = {
    "verifiedai.analytics.event-catalog-enabled=true",
    "verifiedai.analytics.product-metrics-enabled=true"
})
final class ProductMetricsAdminControllerIT extends PostgresIntegrationTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();

    @Value("${local.server.port}")
    int port;

    @Autowired
    IdentityApplicationService identityApplicationService;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    AnalyticsEventPort analyticsEventPort;

    @Autowired
    ProductMetricsJdbcRepository metricsRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("""
            truncate table
                analytics_daily_metric_rollups,
                analytics_events,
                ai_usage_records,
                entitlement_quota_consumptions,
                entitlement_quota_ledgers,
                entitlements,
                refresh_tokens,
                sessions,
                user_identities,
                users
            cascade
            """);
    }

    @Test
    void enforcesRoleAdminAuthzFeatureFlagAndAccurateMetricsRollups() throws Exception {
        Method method = ProductMetricsAdminController.class.getMethod(
            "getProductMetrics",
            String.class,
            String.class,
            String.class
        );
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasRole('ADMIN')");

        // 401 unauthenticated on both routes
        for (String prefix : new String[]{"/api/v1", "/v1"}) {
            HttpResponse<String> unauth = httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + prefix + "/admin/analytics/product-metrics"))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
            assertThat(unauth.statusCode()).isEqualTo(401);
            assertThat(unauth.body()).contains("\"code\":\"AUTH_TOKEN_EXPIRED\"");
        }

        // 403 ANALYTICS_ADMIN_FORBIDDEN for regular learner JWT
        AuthSessionResult learner = signIn("anl-regular-learner");
        for (String prefix : new String[]{"/api/v1", "/v1"}) {
            HttpResponse<String> forbidden = httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + prefix + "/admin/analytics/product-metrics"))
                    .header("Authorization", "Bearer " + learner.accessToken())
                    .header("Accept", "application/json")
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
            assertThat(forbidden.statusCode()).isEqualTo(403);
            assertThat(forbidden.body()).contains("\"code\":\"ANALYTICS_ADMIN_FORBIDDEN\"");
        }

        // 503 ANALYTICS_FEATURE_DISABLED when product-metrics-enabled=false
        ProductMetricsQueryService disabledQueryService = new ProductMetricsQueryService(
            metricsRepository,
            AnalyticsProperties.defaults(),
            new AnalyticsMetrics(new SimpleMeterRegistry()),
            Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneOffset.UTC)
        );
        MockMvc disabledMvc = MockMvcBuilders
            .standaloneSetup(new ProductMetricsAdminController(disabledQueryService))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
        disabledMvc.perform(get("/api/v1/admin/analytics/product-metrics"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("ANALYTICS_FEATURE_DISABLED"));

        // Seed deterministic events for two users (FREE and PRO) inside [2026-11-17 .. 2026-11-23]
        AuthSessionResult proUser = signIn("anl-pro-learner");
        AuthSessionResult adminSession = signIn("anl-admin-operator");
        String adminJwt = issueAdminJwt(adminSession);

        Instant t1 = Instant.parse("2026-11-20T10:00:00Z");
        Instant t2 = Instant.parse("2026-11-20T12:00:00Z");

        recordServer(learner.userId(), "m1", "onboarding_started", "identity", "FREE", null, 0L, Map.of("locale", "en"), t1);
        recordServer(learner.userId(), "m2", "onboarding_completed", "identity", "FREE", null, 0L, Map.of("locale", "en"), t1.plusSeconds(60));
        recordServer(learner.userId(), "m3", "first_solution_completed", "solving", "FREE", 1000, 10_000L, Map.of("problemType", "LINEAR"), t1.plusSeconds(120));
        recordServer(learner.userId(), "m4", "solve_completed", "solving", "FREE", 2000, 20_000L, Map.of("problemType", "LINEAR"), t1.plusSeconds(180));
        recordServer(learner.userId(), "m5", "mistake_detected", "mistake", "FREE", null, 0L, Map.of("mistakeCode", "SIGN_FLIP"), t1.plusSeconds(240));
        recordServer(learner.userId(), "m6", "twin_drill.completed", "mistake", "FREE", null, 0L,
            Map.of("variant", "A", "evidenceClass", "INDEPENDENT", "passed", true), t1.plusSeconds(600));
        recordServer(learner.userId(), "m7", "twin_drill.completed", "mistake", "FREE", null, 0L,
            Map.of("variant", "B", "evidenceClass", "INDEPENDENT", "passed", true), t1.plusSeconds(900));

        recordServer(proUser.userId(), "p1", "onboarding_started", "identity", "PRO", null, 0L, Map.of("locale", "tr"), t2);
        recordServer(proUser.userId(), "p2", "solve_completed", "solving", "PRO", 3000, 30_000L, Map.of("problemType", "QUADRATIC"), t2.plusSeconds(100));
        recordServer(proUser.userId(), "p3", "solve_completed", "solving", "PRO", 4000, 40_000L, Map.of("problemType", "QUADRATIC"), t2.plusSeconds(200));
        recordServer(proUser.userId(), "p4", "retrieval_check.completed", "mastery", "PRO", null, 0L,
            Map.of("stage", "48H", "passedIndependent", true), t2.plusSeconds(300));
        recordServer(proUser.userId(), "p5", "retrieval_check.completed", "mastery", "PRO", null, 0L,
            Map.of("stage", "7D", "passedIndependent", false), t2.plusSeconds(400));
        recordServer(proUser.userId(), "p6", "purchase_completed", "billing", "PRO", null, 0L,
            Map.of("packageCode", "PRO_MONTHLY", "toTier", "PRO"), t2.plusSeconds(500));

        // 422 ANALYTICS_METRICS_WINDOW_INVALID for > 90 days or reversed dates
        HttpResponse<String> invalidWindow = httpClient.send(
            HttpRequest.newBuilder(URI.create(
                    "http://localhost:" + port + "/api/v1/admin/analytics/product-metrics?from=2026-01-01&to=2026-06-01"))
                .header("Authorization", "Bearer " + adminJwt)
                .header("Accept", "application/json")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(invalidWindow.statusCode()).isEqualTo(422);
        assertThat(invalidWindow.body()).contains("\"code\":\"ANALYTICS_METRICS_WINDOW_INVALID\"");

        HttpResponse<String> reversedWindow = httpClient.send(
            HttpRequest.newBuilder(URI.create(
                    "http://localhost:" + port + "/v1/admin/analytics/product-metrics?from=2026-11-23&to=2026-11-20"))
                .header("Authorization", "Bearer " + adminJwt)
                .header("Accept", "application/json")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(reversedWindow.statusCode()).isEqualTo(422);
        assertThat(reversedWindow.body()).contains("\"code\":\"ANALYTICS_METRICS_WINDOW_INVALID\"");

        // 200 OK with Cache-Control: no-store, private on both routes
        for (String prefix : new String[]{"/api/v1", "/v1"}) {
            HttpResponse<String> ok = httpClient.send(
                HttpRequest.newBuilder(URI.create(
                        "http://localhost:" + port + prefix + "/admin/analytics/product-metrics?from=2026-11-17&to=2026-11-23&tier=ALL"))
                    .header("Authorization", "Bearer " + adminJwt)
                    .header("Accept", "application/json")
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
            assertThat(ok.statusCode()).isEqualTo(200);
            assertThat(ok.headers().firstValue("Cache-Control").orElse(""))
                .contains("no-store")
                .contains("private");

            JsonNode tree = OBJECT_MAPPER.readTree(ok.body());
            assertThat(tree.get("schemaVersion").asText()).isEqualTo("product-metrics-summary-v1");
            assertThat(tree.get("llmTokensUsed").asInt()).isZero();
            assertThat(tree.get("trainingEligible").asBoolean()).isFalse();

            assertThat(tree.path("activationFunnel").get("onboardingStartedUsers").asLong()).isEqualTo(2L);
            assertThat(tree.path("activationFunnel").get("onboardingCompletedUsers").asLong()).isEqualTo(1L);
            assertThat(tree.path("activationFunnel").get("subscribedUsers").asLong()).isEqualTo(1L);

            assertThat(tree.path("learningOsKpis").path("vitrr").get("rate").asDouble()).isEqualTo(1.0);
            assertThat(tree.path("learningOsKpis").path("drpr48h").get("rate").asDouble()).isEqualTo(1.0);
            assertThat(tree.path("learningOsKpis").path("drpr7d").get("rate").asDouble()).isEqualTo(0.0);
            assertThat(tree.path("learningOsKpis").path("mrrr14d").get("insufficientData").asBoolean()).isTrue();
            assertThat(tree.path("learningOsKpis").path("transferGainIndex").get("variantAAccuracy").asDouble()).isEqualTo(1.0);
            assertThat(tree.path("learningOsKpis").path("transferGainIndex").get("variantBCAccuracy").asDouble()).isEqualTo(1.0);
            assertThat(tree.path("learningOsKpis").path("transferGainIndex").get("transferGainIndex").asDouble()).isEqualTo(0.0);

            // Latency samples [1000, 2000, 3000, 4000] -> continuous p50 = 2500, p95 = 3850
            assertThat(tree.path("qualityAndLatency").get("latencySampleCount").asLong()).isEqualTo(4L);
            assertThat(tree.path("qualityAndLatency").get("solveLatencyP50Ms").asInt()).isEqualTo(2500);
            assertThat(tree.path("qualityAndLatency").get("solveLatencyP95Ms").asInt()).isEqualTo(3850);
            assertThat(tree.path("qualityAndLatency").get("insufficientLatencyData").asBoolean()).isFalse();

            // Total AI cost = (10k + 20k + 30k + 40k) = 100k micros = $0.10 across 4 completed solves -> $0.025/solve
            assertThat(tree.path("conversionAndUnitEconomics").get("totalCompletedSolves").asLong()).isEqualTo(4L);
            assertThat(tree.path("conversionAndUnitEconomics").get("totalAiCostUsd").asDouble()).isEqualTo(0.1);
            assertThat(tree.path("conversionAndUnitEconomics").get("aiCostPerCompletedSolveUsd").asDouble()).isEqualTo(0.025);
        }
    }

    private void recordServer(
        UUID userId,
        String clientEventId,
        String eventName,
        String sourceModule,
        String tier,
        Integer latencyMs,
        long aiCostMicros,
        Map<String, Object> properties,
        Instant occurredAt
    ) {
        var outcome = analyticsEventPort.recordServerEvent(
            RecordAnalyticsEventCommand.of(
                userId,
                "it-adm-" + clientEventId,
                eventName,
                sourceModule,
                tier,
                "en",
                latencyMs,
                aiCostMicros,
                properties,
                "corr-" + clientEventId,
                occurredAt
            )
        );
        assertThat(outcome.status()).isEqualTo("ACCEPTED");
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

    @TestConfiguration
    static class AuthTestConfiguration {
        @Bean
        @Primary
        AppleIdentityVerifier appleIdentityVerifier() {
            return (identityToken, rawNonce) -> new VerifiedAppleIdentity(identityToken);
        }
    }
}
