package com.verifiedai.notification.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.verifiedai.identity.application.AppleSignInCommand;
import com.verifiedai.identity.application.AuthSessionResult;
import com.verifiedai.identity.application.IdentityApplicationService;
import com.verifiedai.identity.domain.model.AppleIdentityVerifier;
import com.verifiedai.identity.domain.model.VerifiedAppleIdentity;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.notification.application.NotificationDeviceService;
import com.verifiedai.notification.application.NotificationDispatchService;
import com.verifiedai.notification.application.NotificationMetrics;
import com.verifiedai.notification.application.NotificationPreferenceService;
import com.verifiedai.notification.application.NotificationSchedulingPort;
import com.verifiedai.notification.application.NotificationSchedulingService;
import com.verifiedai.notification.application.ScheduleNotificationCommand;
import com.verifiedai.notification.application.ScheduledNotificationResult;
import com.verifiedai.notification.domain.NotificationCategory;
import com.verifiedai.notification.domain.NotificationChannel;
import com.verifiedai.notification.domain.NotificationDeliveryStatus;
import com.verifiedai.notification.domain.NotificationIntentType;
import com.verifiedai.notification.domain.NotificationPolicyEvaluator;
import com.verifiedai.notification.domain.NotificationSuppressionReason;
import com.verifiedai.notification.infrastructure.NotificationDeliveryJdbcRepository;
import com.verifiedai.notification.infrastructure.NotificationDeviceJdbcRepository;
import com.verifiedai.notification.infrastructure.NotificationPreferenceJdbcRepository;
import com.verifiedai.notification.infrastructure.NotificationProperties;
import com.verifiedai.sharedkernel.error.ApiExceptionHandler;
import com.verifiedai.sharedkernel.privacy.AccountDataLifecycleContributor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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

@Import(NotificationApiSecurityAndGdprIT.AuthTestConfiguration.class)
@TestPropertySource(properties = {
    "verifiedai.notification.enabled=true",
    "verifiedai.notification.dispatch-worker-enabled=false",
    "verifiedai.notification.apns-live-delivery-enabled=false"
})
final class NotificationApiSecurityAndGdprIT extends PostgresIntegrationTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();

    @Value("${local.server.port}")
    int port;

    @Autowired
    IdentityApplicationService identityApplicationService;

    @Autowired
    NotificationSchedulingPort schedulingPort;

    @Autowired
    NotificationDispatchService dispatchService;

    @Autowired
    NotificationPreferenceJdbcRepository preferenceRepository;

    @Autowired
    NotificationDeviceJdbcRepository deviceRepository;

    @Autowired
    NotificationDeliveryJdbcRepository deliveryRepository;

    @Autowired
    NotificationPolicyEvaluator policyEvaluator;

    @Autowired
    List<AccountDataLifecycleContributor> lifecycleContributors;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("""
            truncate table
                notification_delivery_events,
                notification_deliveries,
                notification_devices,
                notification_preferences,
                entitlement_quota_consumptions,
                entitlement_quota_ledgers,
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
    void enforcesAuthnInactiveAccountAndFeatureDisabledBehavior() throws Exception {
        // 401 unauthenticated
        for (String prefix : new String[]{"/api/v1", "/v1"}) {
            HttpResponse<String> unauth = httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + prefix + "/notifications/preferences"))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
            assertThat(unauth.statusCode()).isEqualTo(401);
            assertThat(unauth.body()).contains("\"code\":\"AUTH_TOKEN_EXPIRED\"");
        }

        // 403 ACCOUNT_NOT_ACTIVE
        AuthSessionResult inactive = signIn("notif-inactive-user");
        jdbcTemplate.update("update users set status = 'DELETION_REQUESTED' where id = ?", inactive.userId());
        for (String prefix : new String[]{"/api/v1", "/v1"}) {
            HttpResponse<String> forbidden = httpClient.send(
                authorized(inactive, prefix + "/notifications/preferences").GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );
            assertThat(forbidden.statusCode()).isEqualTo(403);
            assertThat(forbidden.body()).contains("\"code\":\"ACCOUNT_NOT_ACTIVE\"");
        }

        // 503 NOTIFICATION_FEATURE_DISABLED when flag is off + SPI fail-closed SUPPRESSED(FEATURE_DISABLED)
        AuthSessionResult active = signIn("notif-disabled-flag-user");
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-22T12:00:00Z"), ZoneOffset.UTC);
        NotificationProperties disabledProps = NotificationProperties.defaults();
        NotificationMetrics metrics = new NotificationMetrics(new SimpleMeterRegistry());
        NotificationPreferenceService disabledPrefSvc = new NotificationPreferenceService(
            preferenceRepository, disabledProps, metrics, fixedClock
        );
        NotificationDeviceService disabledDevSvc = new NotificationDeviceService(
            deviceRepository, preferenceRepository, disabledProps, metrics, fixedClock
        );
        NotificationSchedulingService disabledSchedSvc = new NotificationSchedulingService(
            deliveryRepository,
            preferenceRepository,
            deviceRepository,
            policyEvaluator,
            disabledPrefSvc,
            disabledProps,
            metrics,
            fixedClock,
            transactionManager
        );

        MockMvc disabledMvc = MockMvcBuilders
            .standaloneSetup(
                new NotificationPreferenceController(disabledPrefSvc),
                new NotificationDeviceController(disabledDevSvc),
                new NotificationDeliveryController(disabledSchedSvc)
            )
            .setControllerAdvice(new ApiExceptionHandler())
            .setCustomArgumentResolvers(new TestJwtResolver(active.userId(), active.sessionId()))
            .build();

        disabledMvc.perform(get("/api/v1/notifications/preferences"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("NOTIFICATION_FEATURE_DISABLED"));
        disabledMvc.perform(get("/v1/notifications/devices"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("NOTIFICATION_FEATURE_DISABLED"));
        disabledMvc.perform(get("/api/v1/notifications/deliveries"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("NOTIFICATION_FEATURE_DISABLED"));

        ScheduledNotificationResult suppressed = disabledSchedSvc.schedule(new ScheduleNotificationCommand(
            active.userId(),
            "disabled-flag-idem-1",
            NotificationCategory.STUDY_PLAN,
            NotificationIntentType.STUDY_PLAN_DAILY_MISSION,
            NotificationChannel.PUSH,
            "en",
            "studyplan.daily",
            Map.of(),
            "verified-ai://study-plan/today",
            Instant.parse("2026-11-22T12:00:00Z"),
            Instant.parse("2026-11-22T15:00:00Z"),
            "studyplan",
            "corr-disabled"
        ));
        assertThat(suppressed.status()).isEqualTo(NotificationDeliveryStatus.SUPPRESSED);
        assertThat(suppressed.suppressionReason()).isEqualTo(NotificationSuppressionReason.FEATURE_DISABLED);
        assertThat(jdbcTemplate.queryForObject("select count(*) from notification_deliveries", Integer.class))
            .isZero();
    }

    @Test
    void preferencesDevicesDeliveriesBolaAndGdprLifecycleWorkEndToEnd() throws Exception {
        AuthSessionResult userA = signIn("notif-user-a");
        AuthSessionResult userB = signIn("notif-user-b");

        // 1. Default opt-out GET on both /api/v1 and /v1 + Cache-Control header
        for (String prefix : new String[]{"/api/v1", "/v1"}) {
            HttpResponse<String> getRes = httpClient.send(
                authorized(userA, prefix + "/notifications/preferences").GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );
            assertThat(getRes.statusCode()).isEqualTo(200);
            assertThat(getRes.headers().firstValue("Cache-Control").orElse(""))
                .contains("no-store")
                .contains("private");
            assertThat(getRes.body())
                .contains("\"pushEnabled\":false")
                .contains("\"studyPlanEnabled\":false")
                .contains("\"billingTrialReminderEnabled\":false")
                .contains("\"accountSecurityEnabled\":true")
                .contains("\"trainingEligible\":false")
                .contains("\"version\":0");
        }

        // 2. PATCH optimistic update + 409 conflict + 422 validations
        HttpResponse<String> patchOk = patch(userA, "/api/v1/notifications/preferences", """
            {"pushEnabled":true,"studyPlanEnabled":true,"billingTrialReminderEnabled":true,"timezone":"Europe/Istanbul","dailyCap":3,"expectedVersion":0}
            """);
        assertThat(patchOk.statusCode()).isEqualTo(200);
        assertThat(patchOk.headers().firstValue("Cache-Control").orElse(""))
            .contains("no-store")
            .contains("private");
        assertThat(patchOk.body())
            .contains("\"version\":1")
            .contains("\"pushEnabled\":true")
            .contains("\"billingTrialReminderEnabled\":true")
            .contains("\"timezone\":\"Europe/Istanbul\"");

        HttpResponse<String> patchConflict = patch(userA, "/v1/notifications/preferences", """
            {"pushEnabled":false,"expectedVersion":0}
            """);
        assertThat(patchConflict.statusCode()).isEqualTo(409);
        assertThat(patchConflict.body()).contains("\"code\":\"OPTIMISTIC_CONFLICT\"");

        HttpResponse<String> badSecurity = patch(userA, "/api/v1/notifications/preferences", """
            {"accountSecurityEnabled":false,"expectedVersion":1}
            """);
        assertThat(badSecurity.statusCode()).isEqualTo(422);
        assertThat(badSecurity.body()).contains("\"code\":\"NOTIFICATION_VALIDATION_FAILED\"");

        HttpResponse<String> badTz = patch(userA, "/api/v1/notifications/preferences", """
            {"timezone":"Invalid/Zone","expectedVersion":1}
            """);
        assertThat(badTz.statusCode()).isEqualTo(422);
        assertThat(badTz.body()).contains("\"code\":\"NOTIFICATION_TIMEZONE_INVALID\"");

        HttpResponse<String> badQuiet = patch(userA, "/api/v1/notifications/preferences", """
            {"quietHoursStartLocal":"22:00","quietHoursEndLocal":"22:00","expectedVersion":1}
            """);
        assertThat(badQuiet.statusCode()).isEqualTo(422);
        assertThat(badQuiet.body()).contains("\"code\":\"NOTIFICATION_QUIET_HOURS_INVALID\"");

        // 3. Device registration (no raw deviceToken exposed), BOLA revoke, cross-user token takeover
        String sharedRawToken = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123457890";
        HttpResponse<String> regA = post(userA, "/api/v1/notifications/devices", """
            {"platform":"IOS_APNS","environment":"SANDBOX","deviceToken":"%s","osPermissionStatus":"AUTHORIZED","appVersion":"1.0.0","locale":"tr","trainingEligible":false}
            """.formatted(sharedRawToken));
        assertThat(regA.statusCode()).isEqualTo(201);
        assertThat(regA.body())
            .contains("\"tokenLast4\":\"7890\"")
            .doesNotContain(sharedRawToken)
            .doesNotContain("deviceToken");

        String deviceIdA = OBJECT_MAPPER.readTree(regA.body()).get("deviceId").asText();

        HttpResponse<String> regARefresh = post(userA, "/v1/notifications/devices", """
            {"platform":"IOS_APNS","environment":"SANDBOX","deviceToken":"%s","osPermissionStatus":"AUTHORIZED","appVersion":"1.0.1","locale":"tr"}
            """.formatted(sharedRawToken));
        assertThat(regARefresh.statusCode()).isEqualTo(200);

        HttpResponse<String> listA = httpClient.send(
            authorized(userA, "/api/v1/notifications/devices").GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(listA.statusCode()).isEqualTo(200);
        assertThat(listA.body())
            .contains(deviceIdA)
            .contains("\"tokenLast4\":\"7890\"")
            .doesNotContain(sharedRawToken);

        // User B trying to revoke User A's device -> 404 BOLA protection
        HttpResponse<String> foreignDelete = httpClient.send(
            authorized(userB, "/api/v1/notifications/devices/" + deviceIdA).DELETE().build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(foreignDelete.statusCode()).isEqualTo(404);
        assertThat(foreignDelete.body()).contains("\"code\":\"NOTIFICATION_DEVICE_NOT_FOUND\"");

        // Cross-user token takeover: User B registers same token -> User A's device automatically revoked
        HttpResponse<String> regB = post(userB, "/api/v1/notifications/devices", """
            {"platform":"IOS_APNS","environment":"SANDBOX","deviceToken":"%s","osPermissionStatus":"AUTHORIZED","appVersion":"1.0.1","locale":"en"}
            """.formatted(sharedRawToken));
        assertThat(regB.statusCode()).isEqualTo(201);

        HttpResponse<String> listAAfterTakeover = httpClient.send(
            authorized(userA, "/api/v1/notifications/devices").GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(OBJECT_MAPPER.readTree(listAAfterTakeover.body()).get("items").size()).isZero();

        // Register a distinct token for User A so deliveries & GDPR export include an active device
        String tokenA2 = "9999999999999999999999999999999999999999999999999999999999994321";
        HttpResponse<String> regA2 = post(userA, "/api/v1/notifications/devices", """
            {"platform":"IOS_APNS","environment":"SANDBOX","deviceToken":"%s","osPermissionStatus":"AUTHORIZED","appVersion":"1.0.2","locale":"tr"}
            """.formatted(tokenA2));
        assertThat(regA2.statusCode()).isEqualTo(201);

        // 4. Deliveries list, limit validation, BOLA cancel, SENT illegal cancel
        Instant now = Instant.now();
        ScheduledNotificationResult pendingA = schedulingPort.schedule(new ScheduleNotificationCommand(
            userA.userId(),
            "api-cancel-pending-1",
            NotificationCategory.ACCOUNT_SECURITY,
            NotificationIntentType.ACCOUNT_SECURITY_ALERT,
            NotificationChannel.PUSH,
            "tr",
            "account.security.alert",
            Map.of(),
            "verified-ai://account/security",
            now.plusSeconds(3_600),
            now.plusSeconds(7_200),
            "identity",
            "corr-pending"
        ));
        ScheduledNotificationResult dueA = schedulingPort.schedule(new ScheduleNotificationCommand(
            userA.userId(),
            "api-due-sent-1",
            NotificationCategory.ACCOUNT_SECURITY,
            NotificationIntentType.ACCOUNT_SECURITY_ALERT,
            NotificationChannel.PUSH,
            "tr",
            "account.security.alert",
            Map.of(),
            "verified-ai://account/security",
            now.minusSeconds(30),
            now.plusSeconds(7_200),
            "identity",
            "corr-sent"
        ));
        assertThat(dispatchService.dispatchDueBatch(10)).isEqualTo(1);

        HttpResponse<String> badLimit = httpClient.send(
            authorized(userA, "/api/v1/notifications/deliveries?limit=51").GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(badLimit.statusCode()).isEqualTo(400);

        HttpResponse<String> deliveriesA = httpClient.send(
            authorized(userA, "/v1/notifications/deliveries?limit=20").GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(deliveriesA.statusCode()).isEqualTo(200);
        assertThat(deliveriesA.body())
            .contains(pendingA.deliveryId().toString())
            .contains(dueA.deliveryId().toString())
            .doesNotContain(tokenA2);

        // Foreign cancel -> 404
        HttpResponse<String> foreignCancel = post(
            userB,
            "/api/v1/notifications/deliveries/" + pendingA.deliveryId() + "/cancel",
            ""
        );
        assertThat(foreignCancel.statusCode()).isEqualTo(404);
        assertThat(foreignCancel.body()).contains("\"code\":\"NOTIFICATION_DELIVERY_NOT_FOUND\"");

        // Legal cancel on SCHEDULED -> 200
        HttpResponse<String> cancelOk = post(
            userA,
            "/v1/notifications/deliveries/" + pendingA.deliveryId() + "/cancel",
            ""
        );
        assertThat(cancelOk.statusCode()).isEqualTo(200);
        assertThat(cancelOk.body()).contains("\"status\":\"CANCELLED\"");

        // Illegal cancel on SENT -> 409 NOTIFICATION_ILLEGAL_STATE_TRANSITION
        HttpResponse<String> cancelSent = post(
            userA,
            "/api/v1/notifications/deliveries/" + dueA.deliveryId() + "/cancel",
            ""
        );
        assertThat(cancelSent.statusCode()).isEqualTo(409);
        assertThat(cancelSent.body()).contains("\"code\":\"NOTIFICATION_ILLEGAL_STATE_TRANSITION\"");

        // 5. GDPR Export (excludes raw device_token) & Delete (cascades/erases all 4 tables for User A, keeps User B)
        AccountDataLifecycleContributor notifContributor = lifecycleContributors.stream()
            .filter(c -> "notifications".equals(c.category()))
            .findFirst()
            .orElseThrow();

        Map<String, Object> exportA = notifContributor.exportUserData(userA.userId());
        String exportJson = OBJECT_MAPPER.writeValueAsString(exportA);
        assertThat(exportJson)
            .contains("\"tokenLast4\":\"4321\"")
            .contains("\"trainingEligible\":false")
            .doesNotContain(tokenA2)
            .doesNotContain(sharedRawToken);

        notifContributor.deleteUserData(userA.userId(), Instant.now());

        for (String table : new String[]{
            "notification_preferences",
            "notification_devices",
            "notification_deliveries",
            "notification_delivery_events"
        }) {
            Integer userACount = jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where user_id = ?",
                Integer.class,
                userA.userId()
            );
            assertThat(userACount).as("userA rows remaining in " + table).isZero();
        }
        Integer userBDevices = jdbcTemplate.queryForObject(
            "select count(*) from notification_devices where user_id = ? and active = true",
            Integer.class,
            userB.userId()
        );
        assertThat(userBDevices).isEqualTo(1);
    }

    private AuthSessionResult signIn(String subject) {
        return identityApplicationService.signInWithApple(new AppleSignInCommand(subject, "unused-code", "nonce"));
    }

    private HttpRequest.Builder authorized(AuthSessionResult session, String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Authorization", "Bearer " + session.accessToken())
            .header("Accept", "application/json");
    }

    private HttpResponse<String> patch(AuthSessionResult session, String path, String jsonBody) throws Exception {
        return httpClient.send(
            authorized(session, path)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    private HttpResponse<String> post(AuthSessionResult session, String path, String jsonBody) throws Exception {
        return httpClient.send(
            authorized(session, path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
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
