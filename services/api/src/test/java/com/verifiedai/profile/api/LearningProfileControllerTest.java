package com.verifiedai.profile.api;

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

@Import(LearningProfileControllerTest.AuthTestConfiguration.class)
final class LearningProfileControllerTest extends PostgresIntegrationTestSupport {

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
    void profileEndpointRequiresAuthentication() throws Exception {
        HttpResponse<String> response = httpClient.send(
            HttpRequest.newBuilder(uri("/api/v1/me/learning-profile")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":\"AUTH_TOKEN_EXPIRED\"");
    }

    @Test
    void authenticatedUserCanCreateReadAndConflictOnOwnLearningProfile() throws Exception {
        AuthSessionResult session = signIn("profile-api-user");

        HttpResponse<String> missing = httpClient.send(
            authorized(session, "/api/v1/me/learning-profile").GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(missing.statusCode()).isEqualTo(200);
        assertThat(missing.body()).contains("\"exists\":false");
        assertThat(missing.body()).contains("\"onboardingStatus\":\"NOT_STARTED\"");

        HttpResponse<String> saved = httpClient.send(
            authorized(session, "/api/v1/me/learning-profile")
                .method("PATCH", HttpRequest.BodyPublishers.ofString("""
                    {"educationLevel":"HIGH_SCHOOL","preferredLanguage":"en","explanationDepth":"STANDARD","dailyStudyMinutes":30,"timezone":"Europe/Istanbul","goalContext":"Exam prep","completeOnboarding":true}"""))
                .header("Content-Type", "application/json")
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(saved.statusCode()).as(saved.body()).isEqualTo(200);
        assertThat(saved.body()).contains("\"exists\":true");
        assertThat(saved.body()).contains("\"onboardingStatus\":\"COMPLETED\"");
        assertThat(saved.body()).contains("\"version\":0");

        HttpResponse<String> stale = httpClient.send(
            authorized(session, "/api/v1/me/learning-profile")
                .method("PATCH", HttpRequest.BodyPublishers.ofString("""
                    {"educationLevel":"UNIVERSITY","preferredLanguage":"en","explanationDepth":"DEEP","dailyStudyMinutes":45,"timezone":"Europe/Istanbul","completeOnboarding":true,"expectedVersion":99}"""))
                .header("Content-Type", "application/json")
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(stale.statusCode()).isEqualTo(409);
        assertThat(stale.body()).contains("\"code\":\"OPTIMISTIC_CONFLICT\"");
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
