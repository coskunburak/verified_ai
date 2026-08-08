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
