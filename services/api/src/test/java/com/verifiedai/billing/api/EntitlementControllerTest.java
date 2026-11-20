package com.verifiedai.billing.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.verifiedai.identity.application.AppleSignInCommand;
import com.verifiedai.identity.application.AuthSessionResult;
import com.verifiedai.identity.application.IdentityApplicationService;
import com.verifiedai.identity.domain.model.AppleIdentityVerifier;
import com.verifiedai.identity.domain.model.VerifiedAppleIdentity;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(EntitlementControllerTest.AuthTestConfiguration.class)
final class EntitlementControllerTest extends PostgresIntegrationTestSupport {

    @Value("${local.server.port}")
    int port;

    @Autowired
    IdentityApplicationService identityApplicationService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("truncate table entitlements, learning_profiles, auth_security_events, refresh_tokens, sessions, user_identities, users cascade");
    }

    @Test
    void entitlementEndpointRequiresAuthentication() throws Exception {
        HttpResponse<String> response = httpClient.send(
            HttpRequest.newBuilder(uri("/api/v1/me/entitlements")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":\"AUTH_TOKEN_EXPIRED\"");
    }

    @Test
    void authenticatedUserReceivesServerDefaultFreeEntitlement() throws Exception {
        AuthSessionResult session = signIn("entitlement-api-user");

        HttpResponse<String> response = httpClient.send(
            authorized(session, "/api/v1/me/entitlements").GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body()).contains("\"tier\":\"FREE\"");
        assertThat(response.body()).contains("\"source\":\"DEFAULT_FREE\"");
        assertThat(response.body()).contains("\"status\":\"ACTIVE\"");
        assertThat(response.body()).contains("\"BASIC_SOLVE\"");
        assertThat(response.body()).doesNotContain("\"VERIFIED_SOLVE\"");
        assertThat(count("entitlements")).isEqualTo(1);
    }

    @Test
    void clientCannotSelfPromoteWithPublicEntitlementWrite() throws Exception {
        AuthSessionResult session = signIn("entitlement-self-promote-user");

        HttpResponse<String> response = httpClient.send(
            authorized(session, "/api/v1/me/entitlements")
                .POST(HttpRequest.BodyPublishers.ofString("""
                    {"tier":"PRO_PLUS","status":"ACTIVE","source":"CLIENT"}
                    """))
                .header("Content-Type", "application/json")
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(count("entitlements")).isEqualTo(0);
    }

    @Test
    void revokedSessionAccessTokenCannotKeepUsingCurrentUserEndpoint() throws Exception {
        AuthSessionResult session = signIn("entitlement-revoked-session-user");
        identityApplicationService.logout(session.sessionId());

        HttpResponse<String> response = httpClient.send(
            authorized(session, "/api/v1/me/entitlements").GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":\"AUTH_TOKEN_EXPIRED\"");
    }

    @Test
    void twinsRequireAuthenticationAndReturnIdenticalPrivateDocuments() throws Exception {
        var session = signIn("billing-offer-reader");
        for (String resource : new String[]{"offers?locale=tr-TR", "entitlements"}) {
            String first = null;
            for (String prefix : new String[]{"/v1", "/api/v1"}) {
                String path = prefix + "/billing/" + resource;
                var anonymous = httpClient.send(HttpRequest.newBuilder(uri(path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
                assertThat(anonymous.statusCode()).isEqualTo(401);
                var response = httpClient.send(authorized(session, path).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
                if (first != null) assertThat(response.body()).isEqualTo(first);
                first = response.body();
                assertThat(first).contains(resource.startsWith("offers") ? "FEATURE_DISABLED" : "effectiveTier");
            }
        }
    }

    @Test
    void unsupportedOfferLocaleUsesValidationEnvelope() throws Exception {
        var response = httpClient.send(authorized(signIn("invalid-locale"), "/v1/billing/offers?locale=xx")
            .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("REQUEST_VALIDATION_FAILED");
    }

    @Test
    void storageCoachRemainsVisibleToLegacyButOnlyBelongsToItsOwner() throws Exception {
        var owner = signIn("local_dev_coach");
        var other = signIn("local_dev_other");
        httpClient.send(authorized(owner, "/api/v1/me/entitlements").GET().build(), HttpResponse.BodyHandlers.ofString());
        jdbcTemplate.update("update entitlements set tier='PRO_PLUS' where user_id=?", owner.userId());
        var legacy = httpClient.send(authorized(owner, "/api/v1/me/entitlements").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(legacy.body()).contains("\"tier\":\"PRO_PLUS\"").doesNotContain("effectiveTier");
        var projected = httpClient.send(authorized(owner, "/v1/billing/entitlements").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(projected.body()).contains("COACH_PRO", "PRO_PLUS", "MOCK_EXAM").doesNotContain("userId");
        var foreign = httpClient.send(authorized(other, "/v1/billing/entitlements?userId=" + owner.userId())
            .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(foreign.body()).contains("FREE").doesNotContain("COACH_PRO", "PRO_PLUS");
    }

    @Test void bothBillingAliasesRejectRevokedSessions() throws Exception {
        var session = signIn("local_dev_revoked_billing");
        identityApplicationService.logout(session.sessionId());
        for (String prefix : new String[]{"/api/v1/billing/", "/v1/billing/"}) {
            for (String resource : new String[]{"entitlements", "offers"}) {
                var response = httpClient.send(authorized(session, prefix + resource).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(401);
            }
        }
    }

    @Autowired BillingReadController billingReads;

    @Test void mockMvcDisabledResponseHasNoPriceAndNoImpression() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(billingReads).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v1/billing/offers"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.offers").isEmpty())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.enabled").value(false))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.price").doesNotExist());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/v1/billing/interactions/paywall_viewed"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.recorded").value(false));
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

    private Integer count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
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
