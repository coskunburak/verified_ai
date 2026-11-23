package com.verifiedai.analytics.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.verifiedai.analytics.AnalyticsPrivacyLifecycleContributor;
import com.verifiedai.analytics.api.AnalyticsEventController;
import com.verifiedai.analytics.infrastructure.AnalyticsEventJdbcRepository;
import com.verifiedai.analytics.infrastructure.AnalyticsProperties;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Import(AnalyticsEventIngestionAndGdprIT.AuthTestConfiguration.class)
@TestPropertySource(properties = {
    "verifiedai.analytics.event-catalog-enabled=true",
    "verifiedai.analytics.product-metrics-enabled=true"
})
final class AnalyticsEventIngestionAndGdprIT extends PostgresIntegrationTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();

    @Value("${local.server.port}")
    int port;

    @Autowired
    IdentityApplicationService identityApplicationService;

    @Autowired
    AnalyticsEventJdbcRepository analyticsRepository;

    @Autowired
    AnalyticsPrivacyLifecycleContributor privacyContributor;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("""
            truncate table
                analytics_daily_metric_rollups,
                analytics_events,
                entitlements,
                refresh_tokens,
                sessions,
                user_identities,
                users
            cascade
            """);
    }

    @Test
    void enforcesDisabledFlagAuthnActiveAccountSizeLimitAndCatalogContract() throws Exception {
        for (String prefix : new String[]{"/api/v1", "/v1"}) {
            HttpResponse<String> unauthCatalog = httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + prefix + "/analytics/catalog"))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
            assertThat(unauthCatalog.statusCode()).isEqualTo(401);
            assertThat(unauthCatalog.body()).contains("\"code\":\"AUTH_TOKEN_EXPIRED\"");

            HttpResponse<String> unauthEvent = httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + prefix + "/analytics/events"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
            assertThat(unauthEvent.statusCode()).isEqualTo(401);
        }

        AuthSessionResult inactive = signIn("anl-inactive-user");
        jdbcTemplate.update("update users set status = 'DELETION_REQUESTED' where id = ?", inactive.userId());
        HttpResponse<String> inactiveResp = httpClient.send(
            authorized(inactive, "/api/v1/analytics/catalog").GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(inactiveResp.statusCode()).isEqualTo(403);
        assertThat(inactiveResp.body()).contains("\"code\":\"ACCOUNT_NOT_ACTIVE\"");

        // Disabled flag check (503 ANALYTICS_FEATURE_DISABLED + zero writes)
        AuthSessionResult activeUser = signIn("anl-disabled-user");
        AnalyticsEventIngestionService disabledService = new AnalyticsEventIngestionService(
            analyticsRepository,
            AnalyticsProperties.defaults(),
            new AnalyticsMetrics(new SimpleMeterRegistry()),
            Clock.fixed(Instant.parse("2026-11-23T10:00:00Z"), ZoneOffset.UTC),
            transactionManager
        );
        AnalyticsIngestOutcome serverDisabled = disabledService.recordServerEvent(
            RecordAnalyticsEventCommand.of(
                activeUser.userId(),
                "srv-disabled-001",
                "solve_completed",
                "solving",
                "FREE",
                "en",
                120,
                0L,
                Map.of("problemType", "LINEAR"),
                "corr-disabled",
                Instant.parse("2026-11-23T10:00:00Z")
            )
        );
        assertThat(serverDisabled.status()).isEqualTo("REJECTED");
        assertThat(serverDisabled.errorCode()).isEqualTo("ANALYTICS_FEATURE_DISABLED");

        MockMvc disabledMvc = MockMvcBuilders
            .standaloneSetup(new AnalyticsEventController(disabledService))
            .setControllerAdvice(new ApiExceptionHandler())
            .setCustomArgumentResolvers(new TestJwtResolver(activeUser.userId(), activeUser.sessionId()))
            .build();
        disabledMvc.perform(get("/api/v1/analytics/catalog"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("ANALYTICS_FEATURE_DISABLED"));
        disabledMvc.perform(post("/v1/analytics/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"clientEventId":"dis-evt-0001","eventName":"onboarding_started","emittedBy":"CLIENT","occurredAt":"2026-11-23T10:00:00Z","trainingEligible":false}
                    """))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("ANALYTICS_FEATURE_DISABLED"));
        disabledMvc.perform(post("/api/v1/analytics/events/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"events":[{"clientEventId":"dis-evt-0002","eventName":"onboarding_started","emittedBy":"CLIENT","occurredAt":"2026-11-23T10:00:00Z","trainingEligible":false}],"trainingEligible":false}
                    """))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("ANALYTICS_FEATURE_DISABLED"));

        assertThat(jdbcTemplate.queryForObject("select count(*) from analytics_events", Integer.class)).isZero();

        // Active catalog endpoint on both /api/v1 and /v1
        for (String prefix : new String[]{"/api/v1", "/v1"}) {
            HttpResponse<String> catalog = httpClient.send(
                authorized(activeUser, prefix + "/analytics/catalog").GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );
            assertThat(catalog.statusCode()).isEqualTo(200);
            assertThat(catalog.headers().firstValue("Cache-Control").orElse(""))
                .contains("no-store")
                .contains("private");
            JsonNode tree = OBJECT_MAPPER.readTree(catalog.body());
            assertThat(tree.get("schemaVersion").asText()).isEqualTo("analytics-event-catalog-v1");
            assertThat(tree.get("eventCount").asInt()).isEqualTo(56);
            assertThat(tree.get("llmTokensUsed").asInt()).isZero();
            assertThat(tree.get("trainingEligible").asBoolean()).isFalse();
        }
    }

    @Test
    void handles20ThreadConcurrencyIdempotencyConflictOversizedBodyAndMixedBatchWithoutPiiLeak() throws Exception {
        AuthSessionResult user = signIn("anl-concurrency-user");
        String singlePayload = """
            {
              "clientEventId": "concurrent-evt-001",
              "eventName": "verification_details_viewed",
              "emittedBy": "CLIENT",
              "sourceModule": "ios_client",
              "entitlementTier": "FREE",
              "locale": "en",
              "appVersion": "1.6.0",
              "latencyMs": 95,
              "properties": {
                "problemType": "LINEAR_EQUATION",
                "verificationStatus": "VERIFIED"
              },
              "occurredAt": "2026-11-23T10:05:00Z",
              "trainingEligible": false
            }
            """;

        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<HttpResponse<String>>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<HttpResponse<String>> task = () -> {
                ready.countDown();
                start.await();
                return httpClient.send(
                    authorized(user, "/api/v1/analytics/events")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(singlePayload))
                        .build(),
                    HttpResponse.BodyHandlers.ofString()
                );
            };
            futures.add(executor.submit(task));
        }
        ready.await();
        start.countDown();

        int accepted = 0;
        int replayed = 0;
        for (Future<HttpResponse<String>> future : futures) {
            HttpResponse<String> resp = future.get();
            assertThat(resp.statusCode()).isIn(200, 202);
            JsonNode tree = OBJECT_MAPPER.readTree(resp.body());
            accepted += tree.get("acceptedCount").asInt();
            replayed += tree.get("replayedCount").asInt();
            assertThat(tree.get("llmTokensUsed").asInt()).isZero();
            assertThat(tree.get("trainingEligible").asBoolean()).isFalse();
        }
        executor.shutdownNow();

        assertThat(accepted).isEqualTo(1);
        assertThat(replayed).isEqualTo(19);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from analytics_events where user_id = ? and client_event_id = 'concurrent-evt-001'",
            Integer.class,
            user.userId()
        )).isEqualTo(1);

        // Conflicting payload with same clientEventId -> 409 ANALYTICS_IDEMPOTENCY_CONFLICT
        String conflictingPayload = singlePayload.replace("LINEAR_EQUATION", "QUADRATIC_EQUATION");
        HttpResponse<String> conflict = httpClient.send(
            authorized(user, "/v1/analytics/events")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(conflictingPayload))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(conflict.body()).contains("\"code\":\"ANALYTICS_IDEMPOTENCY_CONFLICT\"");

        // Oversized > 65536 bytes -> 413 REQUEST_TOO_LARGE
        String oversized = "{\"padding\":\"" + "a".repeat(66_000) + "\"}";
        HttpResponse<String> tooLarge = httpClient.send(
            authorized(user, "/api/v1/analytics/events/batch")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(oversized))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(tooLarge.statusCode()).isEqualTo(413);
        assertThat(tooLarge.body()).contains("\"code\":\"REQUEST_TOO_LARGE\"");

        // Mixed batch: 1 valid + 1 replay + 1 PII + 1 server-only + 1 generic UI -> zero PII leaked
        String mixedBatch = """
            {
              "events": [
                {
                  "clientEventId": "batch-valid-0001",
                  "eventName": "onboarding_started",
                  "emittedBy": "CLIENT",
                  "sourceModule": "ios_client",
                  "locale": "tr",
                  "properties": {"authMethod": "APPLE", "locale": "tr"},
                  "occurredAt": "2026-11-23T10:10:00Z",
                  "trainingEligible": false
                },
                {
                  "clientEventId": "concurrent-evt-001",
                  "eventName": "verification_details_viewed",
                  "emittedBy": "CLIENT",
                  "sourceModule": "ios_client",
                  "entitlementTier": "FREE",
                  "locale": "en",
                  "appVersion": "1.6.0",
                  "latencyMs": 95,
                  "properties": {"problemType": "LINEAR_EQUATION", "verificationStatus": "VERIFIED"},
                  "occurredAt": "2026-11-23T10:05:00Z",
                  "trainingEligible": false
                },
                {
                  "clientEventId": "batch-pii-0001",
                  "eventName": "verification_details_viewed",
                  "emittedBy": "CLIENT",
                  "properties": {"rawProblemText": "SECRET_RAW_SOLVE_CONTENT_2x=4"},
                  "occurredAt": "2026-11-23T10:11:00Z",
                  "trainingEligible": false
                },
                {
                  "clientEventId": "batch-srv-0001",
                  "eventName": "purchase_completed",
                  "emittedBy": "CLIENT",
                  "properties": {},
                  "occurredAt": "2026-11-23T10:12:00Z",
                  "trainingEligible": false
                },
                {
                  "clientEventId": "batch-ui-0001",
                  "eventName": "button_clicked",
                  "emittedBy": "CLIENT",
                  "properties": {},
                  "occurredAt": "2026-11-23T10:13:00Z",
                  "trainingEligible": false
                }
              ],
              "trainingEligible": false
            }
            """;

        HttpResponse<String> batchResp = httpClient.send(
            authorized(user, "/api/v1/analytics/events/batch")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mixedBatch))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(batchResp.statusCode()).isEqualTo(200);
        assertThat(batchResp.body()).doesNotContain("SECRET_RAW_SOLVE_CONTENT_2x=4");
        JsonNode batchTree = OBJECT_MAPPER.readTree(batchResp.body());
        assertThat(batchTree.get("acceptedCount").asInt()).isEqualTo(1);
        assertThat(batchTree.get("replayedCount").asInt()).isEqualTo(1);
        assertThat(batchTree.get("rejectedCount").asInt()).isEqualTo(3);
        assertThat(batchTree.get("results").get(2).get("errorCode").asText())
            .isEqualTo("ANALYTICS_PII_OR_RAW_CONTENT_REJECTED");
        assertThat(batchTree.get("results").get(3).get("errorCode").asText())
            .isEqualTo("ANALYTICS_EVENT_VALIDATION_FAILED");
        assertThat(batchTree.get("results").get(4).get("errorCode").asText())
            .isEqualTo("ANALYTICS_EVENT_UNKNOWN");

        // Batch > 50 events -> 422 ANALYTICS_BATCH_TOO_LARGE
        StringBuilder oversizedBatch = new StringBuilder("{\"events\":[");
        for (int i = 0; i < 51; i++) {
            if (i > 0) {
                oversizedBatch.append(',');
            }
            oversizedBatch.append("""
                {"clientEventId":"batch-51-%04d","eventName":"problem_capture_started","emittedBy":"CLIENT","occurredAt":"2026-11-23T10:15:00Z","trainingEligible":false}
                """.formatted(i));
        }
        oversizedBatch.append("],\"trainingEligible\":false}");

        HttpResponse<String> tooLargeBatch = httpClient.send(
            authorized(user, "/v1/analytics/events/batch")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(oversizedBatch.toString()))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(tooLargeBatch.statusCode()).isEqualTo(422);
        assertThat(tooLargeBatch.body()).contains("\"code\":\"ANALYTICS_BATCH_TOO_LARGE\"");
    }

    @Test
    void gdprExportOmitsOperationalHashesAndDeletionPreservesOtherUsersAndRollups() throws Exception {
        AuthSessionResult user1 = signIn("anl-gdpr-user1");
        AuthSessionResult user2 = signIn("anl-gdpr-user2");

        for (AuthSessionResult u : List.of(user1, user2)) {
            String body = """
                {
                  "clientEventId": "gdpr-evt-%s",
                  "eventName": "problem_capture_accepted",
                  "emittedBy": "CLIENT",
                  "sourceModule": "ios_client",
                  "sessionRefHash": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                  "properties": {"captureSource": "CAMERA", "assetKind": "IMAGE"},
                  "occurredAt": "2026-11-23T09:00:00Z",
                  "trainingEligible": false
                }
                """.formatted(u.userId().toString().substring(0, 8));
            HttpResponse<String> resp = httpClient.send(
                authorized(u, "/api/v1/analytics/events")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
            assertThat(resp.statusCode()).isEqualTo(202);
        }

        analyticsRepository.refreshDailyRollups(LocalDate.of(2026, 11, 23), Instant.parse("2026-11-23T11:00:00Z"));

        Map<String, Object> exported = privacyContributor.exportUserData(user1.userId());
        assertThat(exported.get("category")).isEqualTo("analytics");
        assertThat(exported.get("analyticsEventCount")).isEqualTo(1);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) exported.get("analyticsEvents");
        assertThat(events).hasSize(1);
        Map<String, Object> row = events.getFirst();
        assertThat(row.get("eventName")).isEqualTo("problem_capture_accepted");
        assertThat(row.get("trainingEligible")).isEqualTo(false);
        assertThat(row).doesNotContainKeys("payloadHash", "payload_hash", "sessionRefHash", "session_ref_hash", "correlationId", "correlation_id");

        privacyContributor.deleteUserData(user1.userId(), Instant.now());

        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from analytics_events where user_id = ?",
            Integer.class,
            user1.userId()
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from analytics_events where user_id = ?",
            Integer.class,
            user2.userId()
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from analytics_daily_metric_rollups",
            Integer.class
        )).isGreaterThanOrEqualTo(1);
    }

    private AuthSessionResult signIn(String subject) {
        return identityApplicationService.signInWithApple(new AppleSignInCommand(subject, "unused-code", "nonce"));
    }

    private HttpRequest.Builder authorized(AuthSessionResult session, String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Authorization", "Bearer " + session.accessToken())
            .header("Accept", "application/json");
    }

    private static final class TestJwtResolver implements HandlerMethodArgumentResolver {
        private final UUID userId;
        private final UUID sessionId;

        TestJwtResolver(UUID userId, UUID sessionId) {
            this.userId = userId;
            this.sessionId = sessionId;
        }

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return Jwt.class.isAssignableFrom(parameter.getParameterType());
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
                .claim("sid", sessionId.toString())
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
