package com.verifiedai.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.identity.domain.model.AppleIdentityVerifier;
import com.verifiedai.identity.domain.model.VerifiedAppleIdentity;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(IdentityApplicationServiceTest.AuthTestConfiguration.class)
final class IdentityApplicationServiceTest extends PostgresIntegrationTestSupport {

    @Autowired
    IdentityApplicationService identityApplicationService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanIdentityTables() {
        jdbcTemplate.execute("truncate table entitlements, learning_profiles, auth_security_events, refresh_tokens, sessions, user_identities, users cascade");
    }

    @Test
    void signInWithAppleCreatesUserIdentityAndSession() {
        AuthSessionResult result = identityApplicationService.signInWithApple(
            new AppleSignInCommand("apple-subject-1", "unused-code", "nonce")
        );

        assertThat(result.userId()).isNotNull();
        assertThat(result.sessionId()).isNotNull();
        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).startsWith("rt_");
        assertThat(count("users")).isEqualTo(1);
        assertThat(count("user_identities")).isEqualTo(1);
        assertThat(count("sessions")).isEqualTo(1);
        assertThat(count("refresh_tokens")).isEqualTo(1);
    }

    @Test
    void signUpWithEmailCreatesCredentialIdentityAndSession() {
        AuthSessionResult result = identityApplicationService.signUpWithEmail(
            new EmailSignUpCommand(" Student@Example.COM ", "Password123")
        );

        assertThat(result.userId()).isNotNull();
        assertThat(result.sessionId()).isNotNull();
        assertThat(count("users")).isEqualTo(1);
        assertThat(countWhere("user_identities", "provider = 'EMAIL' and provider_subject = 'student@example.com'")).isEqualTo(1);
        assertThat(countWhere("user_password_credentials", "email_normalized = 'student@example.com'")).isEqualTo(1);
        String passwordHash = jdbcTemplate.queryForObject(
            "select password_hash from user_password_credentials where email_normalized = 'student@example.com'",
            String.class
        );
        assertThat(passwordHash).isNotEqualTo("Password123").startsWith("$2");
    }

    @Test
    void signInWithEmailUsesExistingCredentialAndRecordsUse() {
        AuthSessionResult signUp = identityApplicationService.signUpWithEmail(
            new EmailSignUpCommand("student@example.com", "Password123")
        );

        AuthSessionResult signIn = identityApplicationService.signInWithEmail(
            new EmailSignInCommand("STUDENT@example.com", "Password123")
        );

        assertThat(signIn.userId()).isEqualTo(signUp.userId());
        assertThat(signIn.sessionId()).isNotEqualTo(signUp.sessionId());
        assertThat(count("users")).isEqualTo(1);
        assertThat(count("sessions")).isEqualTo(2);
        assertThat(countWhere("user_password_credentials", "last_used_at is not null")).isEqualTo(1);
    }

    @Test
    void duplicateEmailSignUpIsRejected() {
        identityApplicationService.signUpWithEmail(new EmailSignUpCommand("student@example.com", "Password123"));

        assertThatThrownBy(() -> identityApplicationService.signUpWithEmail(
            new EmailSignUpCommand("student@example.com", "Password123")
        ))
            .isInstanceOf(ApiProblemException.class)
            .extracting(exception -> ((ApiProblemException) exception).code())
            .isEqualTo(ApiErrorCode.AUTH_EMAIL_ALREADY_REGISTERED);
    }

    @Test
    void wrongEmailPasswordIsRejected() {
        identityApplicationService.signUpWithEmail(new EmailSignUpCommand("student@example.com", "Password123"));

        assertThatThrownBy(() -> identityApplicationService.signInWithEmail(
            new EmailSignInCommand("student@example.com", "WrongPassword123")
        ))
            .isInstanceOf(ApiProblemException.class)
            .extracting(exception -> ((ApiProblemException) exception).code())
            .isEqualTo(ApiErrorCode.AUTH_CREDENTIALS_INVALID);
    }

    @Test
    void continueAsGuestCreatesAnonymousIdentityAndSession() {
        AuthSessionResult result = identityApplicationService.continueAsGuest();

        assertThat(result.userId()).isNotNull();
        assertThat(result.sessionId()).isNotNull();
        assertThat(countWhere("user_identities", "provider = 'GUEST'")).isEqualTo(1);
        assertThat(count("sessions")).isEqualTo(1);
        assertThat(count("refresh_tokens")).isEqualTo(1);
    }

    @Test
    void twoConcurrentFirstLoginsForSameAppleSubjectCreateOneUser() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var barrier = new CyclicBarrier(2);
        try {
            List<Future<AuthSessionResult>> futures = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                futures.add(executor.submit(() -> {
                    barrier.await();
                    return identityApplicationService.signInWithApple(
                        new AppleSignInCommand("apple-concurrent-subject", "unused-code", "nonce")
                    );
                }));
            }

            AuthSessionResult first = futures.get(0).get();
            AuthSessionResult second = futures.get(1).get();

            assertThat(second.userId()).isEqualTo(first.userId());
            assertThat(countWhere("users", "id = '" + first.userId() + "'")).isEqualTo(1);
            assertThat(countWhere("user_identities", "provider_subject = 'apple-concurrent-subject'")).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void refreshRotatesTokenAndReuseRevokesSession() {
        AuthSessionResult login = identityApplicationService.signInWithApple(
            new AppleSignInCommand("apple-subject-rotate", "unused-code", "nonce")
        );
        AuthSessionResult refreshed = identityApplicationService.refresh(login.refreshToken());

        assertThat(refreshed.refreshToken()).isNotEqualTo(login.refreshToken());
        assertThat(countWhere("refresh_tokens", "session_id = '" + login.sessionId() + "'")).isEqualTo(2);

        assertThatThrownBy(() -> identityApplicationService.refresh(login.refreshToken()))
            .isInstanceOf(ApiProblemException.class)
            .extracting(exception -> ((ApiProblemException) exception).code())
            .isEqualTo(ApiErrorCode.AUTH_REFRESH_REVOKED);

        assertThatThrownBy(() -> identityApplicationService.refresh(refreshed.refreshToken()))
            .isInstanceOf(ApiProblemException.class)
            .extracting(exception -> ((ApiProblemException) exception).code())
            .isEqualTo(ApiErrorCode.AUTH_REFRESH_REVOKED);
    }

    private Integer count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    }

    private Integer countWhere(String table, String predicate) {
        return jdbcTemplate.queryForObject("select count(*) from " + table + " where " + predicate, Integer.class);
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
