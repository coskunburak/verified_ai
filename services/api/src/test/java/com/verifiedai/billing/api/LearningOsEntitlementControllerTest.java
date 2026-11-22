package com.verifiedai.billing.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.verifiedai.billing.application.BillingOfferCatalogService;
import com.verifiedai.billing.application.EntitlementApplicationService;
import com.verifiedai.identity.application.AppleSignInCommand;
import com.verifiedai.identity.application.AuthSessionResult;
import com.verifiedai.identity.application.IdentityApplicationService;
import com.verifiedai.identity.domain.model.AppleIdentityVerifier;
import com.verifiedai.identity.domain.model.VerifiedAppleIdentity;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiExceptionHandler;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Import(LearningOsEntitlementControllerTest.AuthTestConfiguration.class)
@TestPropertySource(properties = "verifiedai.billing.learning-os-tiers-enabled=true")
final class LearningOsEntitlementControllerTest extends PostgresIntegrationTestSupport {

    @Value("${local.server.port}")
    int port;

    @Autowired
    IdentityApplicationService identityApplicationService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("""
            truncate table
                billing_entitlement_transition_audit,
                billing_events,
                app_store_notifications,
                app_store_subscriptions,
                app_store_transactions,
                commerce_account_tokens,
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
    void billingEntitlementAliasesReturnPrivateNoStoreFreeBaselineAndIgnoreUserIdParam() throws Exception {
        AuthSessionResult owner = signIn("los-owner");
        AuthSessionResult other = signIn("los-other");

        String v1Body = null;
        for (String prefix : new String[]{"/v1", "/api/v1"}) {
            HttpResponse<String> response = httpClient.send(
                authorized(owner, prefix + "/billing/entitlements?userId=" + other.userId()).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );
            assertThat(response.statusCode()).isEqualTo(200);
            String cacheControl = response.headers().firstValue("Cache-Control").orElse("");
            assertThat(cacheControl).contains("private").contains("no-store");
            assertThat(response.body())
                .contains("\"featureEnabled\":true")
                .contains("\"effectiveTier\":\"FREE\"")
                .contains("\"packageCode\":\"FREE\"")
                .contains("\"paidAccessActive\":false")
                .contains("\"BASIC_SOLVE\"")
                .contains("\"ATTEMPT_VERIFY\"")
                .contains("\"DETERMINISTIC_TWIN\"")
                .contains("\"mistakePassport\":\"SUMMARY\"")
                .doesNotContain("userId")
                .doesNotContain("MICRO_LESSON");

            String normalized = response.body().replaceAll("\"accessEvaluatedAt\":\"[^\"]+\"", "\"accessEvaluatedAt\":\"T\"");
            if (v1Body != null) {
                assertThat(normalized).isEqualTo(v1Body);
            }
            v1Body = normalized;
        }
    }

    @Test
    void expiredOrRevokedPromotionalGrantFallsBackToFreeBaselineWhileLegacyWireStaysIntact() throws Exception {
        AuthSessionResult owner = signIn("los-expired-promo");
        httpClient.send(authorized(owner, "/api/v1/me/entitlements").GET().build(), HttpResponse.BodyHandlers.ofString());

        jdbcTemplate.update(
            "update entitlements set tier='PRO_PLUS', source='PROMOTIONAL', status='ACTIVE', effective_at=now() - interval '2 hours', expires_at=now() - interval '1 hour' where user_id=?",
            owner.userId()
        );

        HttpResponse<String> v2 = httpClient.send(
            authorized(owner, "/v1/billing/entitlements").GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(v2.statusCode()).isEqualTo(200);
        assertThat(v2.body())
            .contains("\"effectiveTier\":\"FREE\"")
            .contains("\"legacyTier\":\"PRO_PLUS\"")
            .contains("\"status\":\"EXPIRED\"")
            .contains("\"packageCode\":\"FREE\"")
            .contains("\"paidAccessActive\":false")
            .contains("\"BASIC_SOLVE\"")
            .contains("\"ATTEMPT_VERIFY\"")
            .contains("\"DETERMINISTIC_TWIN\"")
            .doesNotContain("HYBRID_TWIN")
            .doesNotContain("TIMED_MOCK_SESSION");

        jdbcTemplate.update(
            "update entitlements set tier='PRO_PLUS', source='PROMOTIONAL', status='ACTIVE', expires_at=now() + interval '1 day' where user_id=?",
            owner.userId()
        );
        HttpResponse<String> legacy = httpClient.send(
            authorized(owner, "/api/v1/me/entitlements").GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(legacy.statusCode()).isEqualTo(200);
        assertThat(legacy.body())
            .contains("\"tier\":\"PRO_PLUS\"")
            .doesNotContain("effectiveTier")
            .doesNotContain("learningOsCapabilities");
    }

    @Test
    void rejectsAnonymousRevokedAndInactiveOrDeletedAccountsAcrossBothPrefixes() throws Exception {
        AuthSessionResult disabledUser = signIn("los-disabled");
        jdbcTemplate.update("update users set status = 'DISABLED' where id = ?", disabledUser.userId());

        AuthSessionResult deletedUser = signIn("los-deleted");
        jdbcTemplate.update("update users set status = 'DELETED' where id = ?", deletedUser.userId());

        AuthSessionResult revokedSession = signIn("los-revoked-session");
        identityApplicationService.logout(revokedSession.sessionId());

        for (String prefix : new String[]{"/v1", "/api/v1"}) {
            String path = prefix + "/billing/entitlements";
            HttpResponse<String> anonymous = httpClient.send(
                HttpRequest.newBuilder(uri(path)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );
            assertThat(anonymous.statusCode()).isEqualTo(401);

            HttpResponse<String> revokedResp = httpClient.send(
                authorized(revokedSession, path).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );
            assertThat(revokedResp.statusCode()).isEqualTo(401);
            assertThat(revokedResp.body()).contains("AUTH_TOKEN_EXPIRED");

            HttpResponse<String> disabledResp = httpClient.send(
                authorized(disabledUser, path).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );
            assertThat(disabledResp.statusCode()).isEqualTo(403);
            assertThat(disabledResp.body()).contains("ACCOUNT_NOT_ACTIVE");

            HttpResponse<String> deletedResp = httpClient.send(
                authorized(deletedUser, path).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );
            assertThat(deletedResp.statusCode()).isEqualTo(403);
            assertThat(deletedResp.body()).contains("ACCOUNT_DELETED");
        }
    }

    @Test
    void transientVerificationFailureReturns503RetryProblemDetails() throws Exception {
        EntitlementApplicationService failingEntitlements = mock(EntitlementApplicationService.class);
        when(failingEntitlements.access(any())).thenThrow(new ApiProblemException(
            HttpStatus.SERVICE_UNAVAILABLE,
            ApiErrorCode.ENTITLEMENT_SYNC_FAILED,
            "Entitlement verification unavailable",
            true,
            "RETRY"
        ));
        BillingOfferCatalogService catalog = mock(BillingOfferCatalogService.class);
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        var mvc = MockMvcBuilders.standaloneSetup(new BillingReadController(catalog, failingEntitlements))
            .setControllerAdvice(new ApiExceptionHandler())
            .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
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
                    return new Jwt(
                        "synthetic-token",
                        Instant.now().minusSeconds(60),
                        Instant.now().plusSeconds(3600),
                        Map.of("alg", "none"),
                        Map.of("sub", userId.toString(), "sid", sessionId.toString())
                    );
                }
            })
            .build();

        mvc.perform(get("/v1/billing/entitlements"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("ENTITLEMENT_SYNC_FAILED"))
            .andExpect(jsonPath("$.details.recoverable").value(true))
            .andExpect(jsonPath("$.details.userAction").value("RETRY"));
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

    @TestConfiguration
    static class AuthTestConfiguration {
        @Bean
        @Primary
        AppleIdentityVerifier appleIdentityVerifier() {
            return (identityToken, rawNonce) -> new VerifiedAppleIdentity(identityToken);
        }
    }
}
