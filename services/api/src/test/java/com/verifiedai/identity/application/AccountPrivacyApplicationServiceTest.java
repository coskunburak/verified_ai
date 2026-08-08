package com.verifiedai.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.billing.application.AppleBillingApplicationService;
import com.verifiedai.billing.application.EntitlementApplicationService;
import com.verifiedai.identity.domain.model.AppleIdentityVerifier;
import com.verifiedai.identity.domain.model.VerifiedAppleIdentity;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.problem.application.ProblemAssetUploadApplicationService;
import com.verifiedai.problem.application.ProblemAssetUploadCommand;
import com.verifiedai.problem.application.ProblemAssetUploadReservationResult;
import com.verifiedai.problem.domain.port.PresignedProblemAssetUpload;
import com.verifiedai.problem.domain.port.ProblemAssetObjectMetadata;
import com.verifiedai.problem.domain.port.ProblemAssetObjectNotFoundException;
import com.verifiedai.problem.domain.port.ProblemAssetStorage;
import com.verifiedai.profile.application.LearningProfileApplicationService;
import com.verifiedai.profile.application.UpdateLearningProfileCommand;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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

    @Autowired
    ProblemAssetUploadApplicationService problemAssetUploadApplicationService;

    @Autowired
    PrivacyTestProblemAssetStorage problemAssetStorage;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("""
            truncate table
                problem_assets,
                problem_sessions,
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
        problemAssetStorage.reset();
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
    void exportAndConfirmedDeletionIncludeProblemAssetLifecycleAndDeleteRawObjects() {
        AuthSessionResult session = signedInProfiledUser("problem-asset-privacy-user");
        ProblemAssetUploadReservationResult reservation = problemAssetUploadApplicationService.reserve(
            session.userId(),
            "privacy-reserve",
            imageUploadCommand()
        );
        String objectKey = objectKey(reservation.problemAssetId());
        problemAssetStorage.put(objectKey, "image/jpeg", 11L, "b".repeat(64));
        problemAssetUploadApplicationService.complete(session.userId(), reservation.uploadId(), "privacy-complete");

        DataExportResult export = accountPrivacyApplicationService.requestExport(session.userId());
        Map<String, Object> content = accountPrivacyApplicationService.downloadExport(session.userId(), export.exportId());

        assertThat(content).containsKey("problemAssets");
        assertThat(content.toString()).contains("rawBinaryIncluded=false");
        assertThat(content.toString()).contains(reservation.problemAssetId().toString());
        assertThat(content.toString()).contains("TEMPORARY_RAW");
        accountPrivacyApplicationService.requestDeletion(session.userId());
        accountPrivacyApplicationService.confirmDeletion(session.userId(), "DELETE");

        assertThat(count("problem_assets")).isEqualTo(0);
        assertThat(count("problem_sessions")).isEqualTo(0);
        assertThat(problemAssetStorage.deletedKeys()).contains(objectKey);
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

    private ProblemAssetUploadCommand imageUploadCommand() {
        return new ProblemAssetUploadCommand(
            "camera",
            "image",
            "image/jpeg",
            11L,
            "b".repeat(64),
            1200,
            900,
            null,
            0.0,
            0.0,
            1.0,
            1.0
        );
    }

    private String objectKey(UUID assetId) {
        return jdbcTemplate.queryForObject("select object_key from problem_assets where id = ?", String.class, assetId);
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

        @Bean
        @Primary
        PrivacyTestProblemAssetStorage problemAssetStorage() {
            return new PrivacyTestProblemAssetStorage();
        }
    }

    static final class PrivacyTestProblemAssetStorage implements ProblemAssetStorage {
        private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();
        private final Set<String> deletedKeys = new LinkedHashSet<>();

        @Override
        public PresignedProblemAssetUpload presignPut(String objectKey, String contentType, long sizeBytes, Duration ttl) {
            return new PresignedProblemAssetUpload(
                URI.create("http://127.0.0.1:9000/verified-ai-problem-assets-local/" + objectKey),
                Instant.now().plus(ttl),
                Map.of("Content-Type", contentType)
            );
        }

        @Override
        public ProblemAssetObjectMetadata head(String objectKey) {
            StoredObject object = objects.get(objectKey);
            if (object == null) {
                throw new ProblemAssetObjectNotFoundException("missing");
            }
            return new ProblemAssetObjectMetadata(object.sizeBytes(), object.contentType());
        }

        @Override
        public String sha256Hex(String objectKey) {
            StoredObject object = objects.get(objectKey);
            if (object == null) {
                throw new ProblemAssetObjectNotFoundException("missing");
            }
            return object.checksumSha256();
        }

        @Override
        public void deleteIfExists(String objectKey) {
            objects.remove(objectKey);
            deletedKeys.add(objectKey);
        }

        void put(String objectKey, String contentType, long sizeBytes, String checksumSha256) {
            objects.put(objectKey, new StoredObject(contentType, sizeBytes, checksumSha256));
        }

        List<String> deletedKeys() {
            return List.copyOf(deletedKeys);
        }

        void reset() {
            objects.clear();
            deletedKeys.clear();
        }

        private record StoredObject(String contentType, long sizeBytes, String checksumSha256) {
        }
    }
}
