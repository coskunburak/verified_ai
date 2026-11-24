package com.verifiedai.growth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.verifiedai.growth.application.GrowthMetrics;
import com.verifiedai.growth.application.ReferralAttributionService;
import com.verifiedai.growth.application.ShareableArtifactService;
import com.verifiedai.growth.application.StoreReviewPromptService;
import com.verifiedai.growth.infrastructure.GrowthProperties;
import com.verifiedai.growth.infrastructure.GrowthReadJdbcRepository;
import com.verifiedai.growth.infrastructure.GrowthWriteJdbcRepository;
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

@Import(GrowthApiSecurityAndRedactionIT.AuthTestConfiguration.class)
@TestPropertySource(properties = {
    "verifiedai.growth.sharing-enabled=true",
    "verifiedai.growth.referral-enabled=true",
    "verifiedai.growth.store-review-enabled=true"
})
final class GrowthApiSecurityAndRedactionIT extends PostgresIntegrationTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();

    @Value("${local.server.port}")
    int port;

    @Autowired
    IdentityApplicationService identityApplicationService;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    GrowthReadJdbcRepository readRepository;

    @Autowired
    GrowthWriteJdbcRepository writeRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void resetState() {
        jdbcTemplate.execute("""
            truncate table
                growth_store_review_events,
                growth_shareable_artifacts,
                growth_referral_attributions,
                growth_referral_codes,
                wrong_answer_reports,
                problem_sessions,
                refresh_tokens,
                sessions,
                user_identities,
                users
            cascade
            """);
    }

    @Test
    void enforcesAuthFailClosedDefaultsBolaPublicRedactionAndReviewEligibility() throws Exception {
        // 1. Fail-closed 503 checks using standalone MockMvc with GrowthProperties.defaults()
        UUID standaloneUserId = UUID.fromString("00000000-0000-0000-0000-00000000CC01");
        GrowthMetrics disabledMetrics = new GrowthMetrics(new SimpleMeterRegistry());
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-24T12:00:00Z"), ZoneOffset.UTC);
        GrowthProperties defaults = GrowthProperties.defaults();

        ShareableArtifactService disabledSharing = new ShareableArtifactService(
            writeRepository, readRepository, defaults, null, disabledMetrics, fixedClock
        );
        ReferralAttributionService disabledReferral = new ReferralAttributionService(
            writeRepository, readRepository, defaults, null, disabledMetrics, fixedClock
        );
        StoreReviewPromptService disabledReview = new StoreReviewPromptService(
            writeRepository, readRepository, defaults, null, disabledMetrics, fixedClock
        );

        MockMvc disabledMockMvc = MockMvcBuilders
            .standaloneSetup(
                new GrowthSharingController(disabledSharing),
                new GrowthReferralController(disabledReferral),
                new GrowthStoreReviewController(disabledReview)
            )
            .setControllerAdvice(new ApiExceptionHandler())
            .setCustomArgumentResolvers(new TestJwtResolver(standaloneUserId))
            .build();

        disabledMockMvc.perform(post("/api/v1/growth/shares")
                .header("Idempotency-Key", "share-disabled-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"artifactType\":\"MASTERY_MILESTONE\",\"summaryPayload\":{\"skillCode\":\"M1\"},\"trainingEligible\":false}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("GROWTH_FEATURE_DISABLED"));

        disabledMockMvc.perform(get("/api/v1/growth/referrals/me"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("GROWTH_FEATURE_DISABLED"));

        disabledMockMvc.perform(get("/api/v1/growth/review-prompts/eligibility")
                .param("triggerMoment", "INDEPENDENT_TWIN_MASTERED"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("GROWTH_FEATURE_DISABLED"));

        // 2. Unauthenticated access to protected routes -> 401
        HttpResponse<String> unauthShares = httpClient.send(
            HttpRequest.newBuilder(uri("/api/v1/growth/shares")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(unauthShares.statusCode()).isEqualTo(401);

        HttpResponse<String> unauthReferrals = httpClient.send(
            HttpRequest.newBuilder(uri("/api/v1/growth/referrals/me")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(unauthReferrals.statusCode()).isEqualTo(401);

        HttpResponse<String> unauthReview = httpClient.send(
            HttpRequest.newBuilder(uri("/api/v1/growth/review-prompts/eligibility?triggerMoment=INDEPENDENT_TWIN_MASTERED"))
                .GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(unauthReview.statusCode()).isEqualTo(401);

        // 3. Sign in two learners (Owner & Foreign)
        AuthSessionResult ownerSession = signIn("apple-growth-owner-1");
        AuthSessionResult foreignSession = signIn("apple-growth-foreign-2");
        String ownerToken = issueJwt(ownerSession, List.of("LEARNER"), List.of());
        String foreignToken = issueJwt(foreignSession, List.of("LEARNER"), List.of());

        // 4. Create share card -> 201 Created + Cache-Control: no-store, private
        String createShareBody = """
            {
              "artifactType": "COUNTERFACTUAL_CHALLENGE_CARD",
              "locale": "en",
              "displayAlias": "Deniz K",
              "summaryPayload": {
                "mistakeCode": "SIGN_FLIP_LINEAR",
                "skillCode": "MATH.ALG.LINEAR_EQ",
                "challengeSummaryKey": "challenge.sign_flip.v1",
                "locale": "en",
                "difficultyBand": "FOUNDATION"
              },
              "ttlDays": 30,
              "trainingEligible": false
            }
            """;
        HttpResponse<String> createResp = sendJson(
            "POST",
            "/api/v1/growth/shares",
            createShareBody,
            ownerToken,
            "http-share-create-1"
        );
        assertThat(createResp.statusCode()).isEqualTo(201);
        assertNoStorePrivate(createResp);
        JsonNode createdJson = OBJECT_MAPPER.readTree(createResp.body());
        String shareId = createdJson.path("shareId").asText();
        String shareToken = createdJson.path("shareToken").asText();
        assertThat(createdJson.path("rawContentRedacted").asBoolean()).isTrue();
        assertThat(createdJson.path("trainingEligible").asBoolean()).isFalse();
        assertThat(createdJson.path("llmTokensUsed").asInt()).isZero();

        // 5. Foreign revoke -> 404 GROWTH_SHARE_NOT_FOUND (BOLA blocked)
        HttpResponse<String> foreignRevokeResp = sendJson(
            "POST",
            "/api/v1/growth/shares/" + shareId + "/revoke",
            "{}",
            foreignToken,
            "http-foreign-revoke-1"
        );
        assertThat(foreignRevokeResp.statusCode()).isEqualTo(404);
        assertThat(OBJECT_MAPPER.readTree(foreignRevokeResp.body()).path("code").asText())
            .isEqualTo("GROWTH_SHARE_NOT_FOUND");

        // 6. Public unauthenticated resolution -> 200 OK, rawContentRedacted == true, zero userId or raw text
        HttpResponse<String> publicResp = httpClient.send(
            HttpRequest.newBuilder(uri("/api/v1/growth/shares/public/" + shareToken)).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(publicResp.statusCode()).isEqualTo(200);
        assertThat(publicResp.headers().firstValue(HttpHeaders.CACHE_CONTROL).orElse("")).contains("no-store");
        JsonNode publicJson = OBJECT_MAPPER.readTree(publicResp.body());
        assertThat(publicJson.has("userId")).isFalse();
        assertThat(publicJson.path("rawContentRedacted").asBoolean()).isTrue();
        assertThat(publicJson.path("trainingEligible").asBoolean()).isFalse();
        assertThat(publicJson.path("llmTokensUsed").asInt()).isZero();
        assertThat(publicResp.body()).doesNotContain(ownerSession.userId().toString());

        // 7. Owner revokes -> subsequent public resolution returns 410 Gone (GROWTH_SHARE_UNAVAILABLE)
        HttpResponse<String> ownerRevokeResp = sendJson(
            "POST",
            "/api/v1/growth/shares/" + shareId + "/revoke",
            "{}",
            ownerToken,
            "http-owner-revoke-1"
        );
        assertThat(ownerRevokeResp.statusCode()).isEqualTo(200);
        assertThat(OBJECT_MAPPER.readTree(ownerRevokeResp.body()).path("status").asText()).isEqualTo("REVOKED");

        HttpResponse<String> publicAfterRevokeResp = httpClient.send(
            HttpRequest.newBuilder(uri("/api/v1/growth/shares/public/" + shareToken)).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(publicAfterRevokeResp.statusCode()).isEqualTo(410);
        assertThat(OBJECT_MAPPER.readTree(publicAfterRevokeResp.body()).path("code").asText())
            .isEqualTo("GROWTH_SHARE_UNAVAILABLE");

        // 8. Store review eligibility: active timer -> friction -> eligible -> cooldown after PROMPT_SHOWN
        jdbcTemplate.update(
            "UPDATE users SET created_at = NOW() - INTERVAL '10 days' WHERE id = ?",
            ownerSession.userId()
        );
        UUID psId1 = UUID.randomUUID();
        UUID psId2 = UUID.randomUUID();
        UUID psId3 = UUID.randomUUID();
        for (UUID psId : List.of(psId1, psId2, psId3)) {
            jdbcTemplate.update(
                "INSERT INTO problem_sessions (id, user_id, status, input_mode, created_at, updated_at, version) "
                    + "VALUES (?, ?, 'CREATED', 'CAMERA', NOW(), NOW(), 0)",
                psId,
                ownerSession.userId()
            );
        }

        // Active timer running -> INELIGIBLE_ACTIVE_TIMER
        HttpResponse<String> timerResp = getWithAuth(
            "/api/v1/growth/review-prompts/eligibility?triggerMoment=INDEPENDENT_TWIN_MASTERED&activeTimerRunning=true",
            ownerToken
        );
        assertThat(timerResp.statusCode()).isEqualTo(200);
        assertThat(OBJECT_MAPPER.readTree(timerResp.body()).path("reasonCode").asText())
            .isEqualTo("INELIGIBLE_ACTIVE_TIMER");

        // Recent wrong_answer_reports friction -> INELIGIBLE_RECENT_FRICTION
        jdbcTemplate.update(
            "INSERT INTO wrong_answer_reports (id, user_id, problem_session_id, idempotency_key, payload_hash, report_reason, status, correlation_id, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'war-friction-1', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 'WRONG_FINAL_ANSWER', 'OPEN', 'corr-1', NOW(), NOW())",
            UUID.randomUUID(),
            ownerSession.userId(),
            psId1
        );
        HttpResponse<String> frictionResp = getWithAuth(
            "/api/v1/growth/review-prompts/eligibility?triggerMoment=INDEPENDENT_TWIN_MASTERED&activeTimerRunning=false",
            ownerToken
        );
        assertThat(frictionResp.statusCode()).isEqualTo(200);
        assertThat(OBJECT_MAPPER.readTree(frictionResp.body()).path("reasonCode").asText())
            .isEqualTo("INELIGIBLE_RECENT_FRICTION");

        // Clear friction -> ELIGIBLE
        jdbcTemplate.update("DELETE FROM wrong_answer_reports WHERE user_id = ?", ownerSession.userId());
        HttpResponse<String> eligibleResp = getWithAuth(
            "/api/v1/growth/review-prompts/eligibility?triggerMoment=INDEPENDENT_TWIN_MASTERED&activeTimerRunning=false",
            ownerToken
        );
        assertThat(eligibleResp.statusCode()).isEqualTo(200);
        assertThat(OBJECT_MAPPER.readTree(eligibleResp.body()).path("eligible").asBoolean()).isTrue();
        assertThat(OBJECT_MAPPER.readTree(eligibleResp.body()).path("reasonCode").asText()).isEqualTo("ELIGIBLE");

        // Record PROMPT_SHOWN -> 201 Created, subsequent check returns INELIGIBLE_COOLDOWN_ACTIVE
        String eventBody = """
            {
              "triggerMoment": "INDEPENDENT_TWIN_MASTERED",
              "outcome": "PROMPT_SHOWN",
              "appVersion": "1.0.0",
              "locale": "en",
              "trainingEligible": false
            }
            """;
        HttpResponse<String> eventResp = sendJson(
            "POST",
            "/api/v1/growth/review-prompts/events",
            eventBody,
            ownerToken,
            "http-review-event-1"
        );
        assertThat(eventResp.statusCode()).isEqualTo(201);
        assertNoStorePrivate(eventResp);

        HttpResponse<String> cooldownResp = getWithAuth(
            "/api/v1/growth/review-prompts/eligibility?triggerMoment=INDEPENDENT_TWIN_MASTERED&activeTimerRunning=false",
            ownerToken
        );
        assertThat(cooldownResp.statusCode()).isEqualTo(200);
        JsonNode cooldownJson = OBJECT_MAPPER.readTree(cooldownResp.body());
        assertThat(cooldownJson.path("eligible").asBoolean()).isFalse();
        assertThat(cooldownJson.path("reasonCode").asText()).isEqualTo("INELIGIBLE_COOLDOWN_ACTIVE");
        assertThat(cooldownJson.path("nextEligibleAt").asText()).isNotBlank();
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
