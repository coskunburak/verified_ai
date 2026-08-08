package com.verifiedai.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.billing.application.AppleBillingApplicationService;
import com.verifiedai.billing.application.EntitlementApplicationService;
import com.verifiedai.identity.domain.model.AppleIdentityVerifier;
import com.verifiedai.identity.domain.model.VerifiedAppleIdentity;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.profile.application.LearningProfileApplicationService;
import com.verifiedai.profile.application.UpdateLearningProfileCommand;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(AccountPrivacyApplicationServiceTest.AuthTestConfiguration.class)
final class AccountPrivacyApplicationServiceTest extends PostgresIntegrationTestSupport {
    @Autowired
    IdentityApplicationService identityApplicationService;

    @Autowired
    LearningProfileApplicationService learningProfileApplicationService;

    @Autowired
    AccountPrivacyApplicationService accountPrivacyApplicationService;

    @Autowired
    AppleBillingApplicationService appleBillingApplicationService;

    @Autowired
    EntitlementApplicationService entitlementApplicationService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("""
            truncate table
                privacy_events,
                data_exports,
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
    void dataExportContainsCurrentUserDataAndExcludesTokenSecrets() {
        AuthSessionResult session = signedInProfiledUser("export-user");

        DataExportResult export = accountPrivacyApplicationService.requestExport(session.userId());
        Map<String, Object> content = accountPrivacyApplicationService.downloadExport(session.userId(), export.exportId());

        assertThat(content).containsKeys("account", "sessions", "learningProfile", "billing");
        assertThat(content.toString()).contains("HIGH_SCHOOL");
        assertThat(content.toString()).contains("DEFAULT_FREE");
        assertThat(content.toString()).doesNotContain(session.refreshToken());
        assertThat(content.toString()).doesNotContain("token_hash");
        assertThat(countWhere("privacy_events", "event_type = 'DATA_EXPORT_DOWNLOADED'")).isEqualTo(1);
    }

    @Test
    void deletionRequestBlocksProfileMutationBeforeConfirmation() {
        AuthSessionResult session = signedInProfiledUser("delete-request-user");

        DeletionRequestResult request = accountPrivacyApplicationService.requestDeletion(session.userId());

        assertThat(request.status()).isEqualTo("DELETION_REQUESTED");
        assertThatThrownBy(() -> learningProfileApplicationService.updateCurrent(session.userId(), profileCommand()))
            .isInstanceOf(ApiProblemException.class)
            .extracting(exception -> ((ApiProblemException) exception).code())
            .isEqualTo(ApiErrorCode.ACCOUNT_NOT_ACTIVE);
    }

    @Test
    void confirmedDeletionRevokesSessionsDeletesProfileAndRetainsMinimizedBillingEvents() {
        AuthSessionResult session = signedInProfiledUser("delete-confirm-user");
        accountPrivacyApplicationService.requestDeletion(session.userId());

        DeletionRequestResult result = accountPrivacyApplicationService.confirmDeletion(session.userId(), "DELETE");

        assertThat(result.status()).isEqualTo("DELETED");
        assertThat(countWhere("sessions", "status = 'REVOKED'")).isEqualTo(1);
        assertThat(countWhere("refresh_tokens", "revoked_at is not null")).isEqualTo(1);
        assertThat(count("learning_profiles")).isEqualTo(0);
        assertThat(countWhere("entitlements", "status = 'REVOKED'")).isEqualTo(1);
        assertThat(countWhere("billing_events", "event_type = 'ACCOUNT_DELETED_COMMERCE_RETAINED'")).isEqualTo(1);
        assertThat(countWhere("privacy_events", "event_type = 'ACCOUNT_DELETED'")).isEqualTo(1);
    }

    @Test
    void confirmedDeletionWinsOverRefreshAndPurchaseConfiguration() {
        AuthSessionResult session = signedInProfiledUser("delete-refresh-user");
        accountPrivacyApplicationService.requestDeletion(session.userId());
        accountPrivacyApplicationService.confirmDeletion(session.userId(), "DELETE");

        assertThatThrownBy(() -> identityApplicationService.refresh(session.refreshToken()))
            .isInstanceOf(ApiProblemException.class)
            .extracting(exception -> ((ApiProblemException) exception).code())
            .isEqualTo(ApiErrorCode.AUTH_REFRESH_REVOKED);
        assertThatThrownBy(() -> appleBillingApplicationService.configuration(session.userId()))
            .isInstanceOf(ApiProblemException.class)
            .extracting(exception -> ((ApiProblemException) exception).code())
            .isEqualTo(ApiErrorCode.ACCOUNT_NOT_ACTIVE);
    }

    @Test
    void sameAppleIdentityCannotSilentlyReattachDeletedTombstone() {
        AuthSessionResult session = signedInProfiledUser("apple-delete-tombstone");
        accountPrivacyApplicationService.requestDeletion(session.userId());
        accountPrivacyApplicationService.confirmDeletion(session.userId(), "DELETE");

        assertThatThrownBy(() -> identityApplicationService.signInWithApple(
            new AppleSignInCommand("apple-delete-tombstone", "unused-code", "nonce")
        ))
            .isInstanceOf(ApiProblemException.class)
            .extracting(exception -> ((ApiProblemException) exception).code())
            .isEqualTo(ApiErrorCode.ACCOUNT_DELETED);
    }

    private AuthSessionResult signedInProfiledUser(String subject) {
        AuthSessionResult session = identityApplicationService.signInWithApple(
            new AppleSignInCommand(subject, "unused-code", "nonce")
        );
        learningProfileApplicationService.updateCurrent(session.userId(), profileCommand());
        entitlementApplicationService.getCurrent(session.userId());
        return session;
    }

    private UpdateLearningProfileCommand profileCommand() {
        return new UpdateLearningProfileCommand(
            "HIGH_SCHOOL",
            "en",
            "STANDARD",
            30,
            "Europe/Istanbul",
            "Prepare for calculus",
            true,
            null
        );
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
