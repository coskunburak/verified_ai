package com.verifiedai.notification.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.verifiedai.identity.application.AppleSignInCommand;
import com.verifiedai.identity.application.AuthSessionResult;
import com.verifiedai.identity.application.IdentityApplicationService;
import com.verifiedai.identity.application.IdentitySecurityNotificationService;
import com.verifiedai.identity.domain.model.AppleIdentityVerifier;
import com.verifiedai.identity.domain.model.VerifiedAppleIdentity;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.notification.application.LifecycleMessagingService;
import com.verifiedai.notification.application.NotificationMetrics;
import com.verifiedai.notification.application.NotificationSchedulingPort;
import com.verifiedai.notification.application.RegisterDeviceCommand;
import com.verifiedai.notification.application.NotificationDeviceService;
import com.verifiedai.notification.infrastructure.LifecycleMessagingJdbcRepository;
import com.verifiedai.notification.infrastructure.NotificationDeliveryJdbcRepository;
import com.verifiedai.notification.infrastructure.NotificationDeviceJdbcRepository;
import com.verifiedai.notification.infrastructure.NotificationPreferenceJdbcRepository;
import com.verifiedai.notification.infrastructure.NotificationProperties;
import com.verifiedai.sharedkernel.error.ApiExceptionHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

@Import(LifecycleSummaryControllerIT.AuthTestConfiguration.class)
@TestPropertySource(properties = {
    "verifiedai.notification.enabled=true",
    "verifiedai.notification.lifecycle-campaigns-enabled=true",
    "verifiedai.notification.lifecycle-scheduler-enabled=false",
    "verifiedai.notification.dispatch-worker-enabled=false",
    "verifiedai.notification.apns-live-delivery-enabled=false"
})
final class LifecycleSummaryControllerIT extends PostgresIntegrationTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();

    @Value("${local.server.port}")
    int port;

    @Autowired
    IdentityApplicationService identityApplicationService;

    @Autowired
    NotificationDeviceService deviceService;

    @Autowired
    IdentitySecurityNotificationService securityNotificationService;

    @Autowired
    NotificationSchedulingPort schedulingPort;

    @Autowired
    NotificationDeliveryJdbcRepository deliveryRepository;

    @Autowired
    NotificationPreferenceJdbcRepository preferenceRepository;

    @Autowired
    NotificationDeviceJdbcRepository deviceRepository;

    @Autowired
    LifecycleMessagingJdbcRepository lifecycleRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("""
            truncate table
                lifecycle_messaging_evaluations,
                lifecycle_messaging_cooldowns,
                notification_delivery_events,
                notification_deliveries,
                notification_devices,
                notification_preferences,
                auth_security_events,
                refresh_tokens,
                sessions,
                user_identities,
                users
            cascade
            """);
    }

    @Test
    void enforcesAuthnDisabledFlagLimitValidationAndStrictUserIsolation() throws Exception {
        // 401 unauthenticated on both route aliases
        for (String prefix : new String[]{"/api/v1", "/v1"}) {
            HttpResponse<String> unauth = httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + prefix + "/notifications/lifecycle-summary"))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
            assertThat(unauth.statusCode()).isEqualTo(401);
            assertThat(unauth.body()).contains("\"code\":\"AUTH_TOKEN_EXPIRED\"");
        }

        // 403 ACCOUNT_NOT_ACTIVE
        AuthSessionResult inactive = signIn("life-inactive-user");
        jdbcTemplate.update("update users set status = 'DELETION_REQUESTED' where id = ?", inactive.userId());
        HttpResponse<String> forbidden = httpClient.send(
            authorized(inactive, "/api/v1/notifications/lifecycle-summary").GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(forbidden.statusCode()).isEqualTo(403);
        assertThat(forbidden.body()).contains("\"code\":\"ACCOUNT_NOT_ACTIVE\"");

        // 503 NOTIFICATION_FEATURE_DISABLED when lifecycle-campaigns-enabled=false
        AuthSessionResult userA = signIn("life-user-a");
        AuthSessionResult userB = signIn("life-user-b");
        LifecycleMessagingService disabledSvc = new LifecycleMessagingService(
            schedulingPort,
            deliveryRepository,
            preferenceRepository,
            deviceRepository,
            lifecycleRepository,
            NotificationProperties.enabledDefaults(),
            new NotificationMetrics(new SimpleMeterRegistry()),
            Clock.fixed(Instant.parse("2026-11-22T12:00:00Z"), ZoneOffset.UTC),
            transactionManager
        );
        MockMvc disabledMvc = MockMvcBuilders
            .standaloneSetup(new NotificationLifecycleController(disabledSvc))
            .setControllerAdvice(new ApiExceptionHandler())
            .setCustomArgumentResolvers(new TestJwtResolver(userA.userId(), userA.sessionId()))
            .build();
        disabledMvc.perform(get("/api/v1/notifications/lifecycle-summary"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("NOTIFICATION_FEATURE_DISABLED"));
        disabledMvc.perform(get("/v1/notifications/lifecycle-summary"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("NOTIFICATION_FEATURE_DISABLED"));

        // Seed User A and User B evaluations separately (BOLA isolation)
        deviceService.registerDevice(userA.userId(), new RegisterDeviceCommand(
            "IOS_APNS", "SANDBOX",
            "7777777777777777777777777777777777777777777777777777777777770001",
            "AUTHORIZED", "1.0.0", "en", false
        ));
        deviceService.registerDevice(userB.userId(), new RegisterDeviceCommand(
            "IOS_APNS", "SANDBOX",
            "8888888888888888888888888888888888888888888888888888888888880002",
            "AUTHORIZED", "1.0.0", "tr", false
        ));

        securityNotificationService.notifySecurityEvent(userA.userId(), "REFRESH_TOKEN_REUSE", "evt-a-only", Instant.now());
        securityNotificationService.notifySecurityEvent(userB.userId(), "SESSION_REVOKED_ALL", "evt-b-only", Instant.now());

        // 400 on invalid limit
        HttpResponse<String> badLimit = httpClient.send(
            authorized(userA, "/api/v1/notifications/lifecycle-summary?limit=51").GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(badLimit.statusCode()).isEqualTo(400);

        // 200 OK with Cache-Control: no-store, private + BOLA isolation on both routes
        for (String prefix : new String[]{"/api/v1", "/v1"}) {
            HttpResponse<String> ok = httpClient.send(
                authorized(userA, prefix + "/notifications/lifecycle-summary?limit=20").GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );
            assertThat(ok.statusCode()).isEqualTo(200);
            assertThat(ok.headers().firstValue("Cache-Control").orElse(""))
                .contains("no-store")
                .contains("private");
            assertThat(ok.body())
                .contains("evt-a-only")
                .doesNotContain("evt-b-only");

            JsonNode tree = OBJECT_MAPPER.readTree(ok.body());
            assertThat(tree.get("schemaVersion").asText()).isEqualTo("lifecycle-summary-v1");
            assertThat(tree.get("userId").asText()).isEqualTo(userA.userId().toString());
            assertThat(tree.get("lifecycleCampaignsEnabled").asBoolean()).isTrue();
            assertThat(tree.get("llmTokensUsed").asInt()).isZero();
            assertThat(tree.get("trainingEligible").asBoolean()).isFalse();
            assertThat(tree.get("recentEvaluations").size()).isEqualTo(1);
        }
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
