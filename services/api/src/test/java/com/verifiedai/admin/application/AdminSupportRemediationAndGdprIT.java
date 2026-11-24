package com.verifiedai.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.admin.infrastructure.AdminSupportWriteJdbcRepository;
import com.verifiedai.billing.application.AppleBillingApplicationService;
import com.verifiedai.billing.domain.model.AppStoreEnvironment;
import com.verifiedai.billing.domain.model.VerifiedAppStoreNotification;
import com.verifiedai.billing.domain.model.VerifiedAppStoreRenewalInfo;
import com.verifiedai.billing.domain.model.VerifiedAppStoreTransaction;
import com.verifiedai.billing.domain.port.AppStoreServerGateway;
import com.verifiedai.billing.domain.port.AppStoreSignedDataVerifier;
import com.verifiedai.billing.domain.port.AppStoreSubscriptionStatusRecord;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;

@Import(AdminSupportRemediationAndGdprIT.BillingStubConfig.class)
@TestPropertySource(properties = {
    "verifiedai.admin.console-enabled=true",
    "verifiedai.admin.remediation-enabled=true",
    "verifiedai.admin.raw-content-access-enabled=true",
    "verifiedai.featureflags.enabled=true",
    "verifiedai.featureflags.admin-mutations-enabled=true",
    "verifiedai.billing.quota-ledger-enabled=true",
    "app.billing.apple.purchase-available=true",
    "app.billing.apple.environment=XCODE",
    "app.billing.apple.products[0].internal-plan-id=pro_monthly",
    "app.billing.apple.products[0].app-store-product-id=com.verifiedai.pro.monthly",
    "app.billing.apple.products[0].entitlement-tier=PRO",
    "app.billing.apple.products[0].subscription-group-id=verifiedai-main",
    "app.billing.apple.products[0].billing-period=P1M"
})
final class AdminSupportRemediationAndGdprIT extends PostgresIntegrationTestSupport {

    @Autowired
    WrongAnswerReportService wrongAnswerReportService;

    @Autowired
    AdminRemediationService remediationService;

    @Autowired
    AdminRawContentAccessService rawContentAccessService;

    @Autowired
    AdminSupportPrivacyLifecycleContributor privacyContributor;

    @Autowired
    AppleBillingApplicationService appleBillingApplicationService;

    @Autowired
    AdminSupportWriteJdbcRepository writeRepository;

    @Autowired
    BillingStubConfig.StubGateway stubGateway;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetState() {
        stubGateway.reset();
        jdbcTemplate.execute("""
            truncate table
                admin_raw_content_access_grants,
                wrong_answer_reports,
                admin_support_audit_logs,
                feature_flag_audit_logs,
                experiment_assignments,
                billing_entitlement_transition_audit,
                entitlement_quota_consumptions,
                entitlement_quota_ledgers,
                app_store_transactions,
                app_store_subscriptions,
                commerce_account_tokens,
                entitlements,
                problem_sessions,
                refresh_tokens,
                sessions,
                user_identities,
                users
            cascade
            """);
        jdbcTemplate.execute("""
            UPDATE feature_flag_definitions
            SET enabled = CASE WHEN flag_key IN ('pdf_upload_enabled', 'strong_model_pct') THEN TRUE ELSE FALSE END,
                kill_switch_active = FALSE,
                rollout_basis_points = CASE WHEN flag_key IN ('pdf_upload_enabled', 'strong_model_pct') THEN 10000 ELSE 0 END,
                current_value_json = default_value_json,
                version = 0,
                updated_at = NOW()
            """);
    }

    @Test
    void handlesConcurrentWrongAnswerSubmissionOptimisticLockAndSafeRemediations() throws Exception {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-00000000B001");
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-00000000A001");
        UUID sessionId = UUID.fromString("00000000-0000-0000-0000-00000000C001");
        seedUserAndSession(userId, sessionId);

        // Concurrent wrong-answer submission with identical (userId, Idempotency-Key) -> 1 row
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<WrongAnswerReportView>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<WrongAnswerReportView> task = () -> {
                ready.countDown();
                go.await();
                return wrongAnswerReportService.submitLearnerReport(
                    userId,
                    sessionId,
                    "WRONG_FINAL_ANSWER",
                    "Step 2 dropped the negative sign",
                    "war-concurrent-key-1"
                );
            };
            futures.add(pool.submit(task));
        }
        ready.await();
        go.countDown();
        List<WrongAnswerReportView> results = new ArrayList<>();
        for (Future<WrongAnswerReportView> f : futures) {
            results.add(f.get());
        }
        pool.shutdown();

        assertThat(results).hasSize(4);
        assertThat(results.stream().map(WrongAnswerReportView::reportId).distinct().count()).isEqualTo(1);
        assertThat(results.stream().filter(r -> !r.replayed()).count()).isEqualTo(1);
        assertThat(results.stream().filter(WrongAnswerReportView::replayed).count()).isEqualTo(3);

        Integer rowCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM wrong_answer_reports WHERE user_id = ?",
            Integer.class,
            userId
        );
        assertThat(rowCount).isEqualTo(1);

        // Mismatched payload with same key -> 409 ADMIN_IDEMPOTENCY_CONFLICT
        assertThatThrownBy(() -> wrongAnswerReportService.submitLearnerReport(
            userId,
            sessionId,
            "PARSE_MISMATCH",
            "Different reason with reused key",
            "war-concurrent-key-1"
        ))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> {
                var p = (ApiProblemException) ex;
                assertThat(p.status()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(p.code()).isEqualTo(ApiErrorCode.ADMIN_IDEMPOTENCY_CONFLICT);
            });

        UUID reportId = results.getFirst().reportId();
        // Stale expectedVersion -> 409 OPTIMISTIC_CONFLICT
        assertThatThrownBy(() -> wrongAnswerReportService.reviewReport(
            reportId,
            "IN_REVIEW",
            null,
            null,
            "Claiming report for review",
            null,
            99L,
            "war-review-stale-1",
            adminId,
            "ROLE_ADMIN"
        ))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> {
                var p = (ApiProblemException) ex;
                assertThat(p.status()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(p.code()).isEqualTo(ApiErrorCode.OPTIMISTIC_CONFLICT);
            });

        // Safe remediation 1: quota-sync
        AdminRemediationResultView quotaSync = remediationService.executeQuotaSync(
            userId,
            "Support verified user quota ledger drift after renewal",
            "rem-quota-sync-001",
            adminId,
            "ROLE_ADMIN"
        );
        assertThat(quotaSync.outcome()).isEqualTo("APPLIED");
        assertThat(quotaSync.action()).isEqualTo("QUOTA_SYNC_EXECUTED");
        Integer ledgerCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM entitlement_quota_ledgers WHERE user_id = ?",
            Integer.class,
            userId
        );
        assertThat(ledgerCount).isEqualTo(1);

        // Safe remediation 2: kill-switch via FeatureFlagGovernanceService
        AdminRemediationResultView killSwitch = remediationService.executeKillSwitch(
            "pdf_upload_enabled",
            true,
            "Mitigating incident INC-108 via emergency PDF kill switch",
            0L,
            "rem-kill-switch-001",
            adminId,
            "ROLE_ADMIN"
        );
        assertThat(killSwitch.outcome()).isEqualTo("APPLIED");
        assertThat(killSwitch.action()).isEqualTo("KILL_SWITCH_LINKED_EXECUTED");
        Boolean killActive = jdbcTemplate.queryForObject(
            "SELECT kill_switch_active FROM feature_flag_definitions WHERE flag_key = 'pdf_upload_enabled'",
            Boolean.class
        );
        assertThat(killActive).isTrue();
        Integer ffAuditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM feature_flag_audit_logs WHERE target_key = 'pdf_upload_enabled'",
            Integer.class
        );
        assertThat(ffAuditCount).isEqualTo(1);

        // Safe remediation 3: billing-reconcile delegating to AppleBillingApplicationService
        UUID appAccountToken = appleBillingApplicationService.configuration(userId).appAccountToken();
        Instant expires = Instant.now().plusSeconds(86400 * 30L);
        stubGateway.setSubscriptionStatuses(List.of(new AppStoreSubscriptionStatusRecord(
            "orig-tx-108",
            1,
            "tx-108|orig-tx-108|com.verifiedai.pro.monthly|" + appAccountToken + "|" + expires,
            ""
        )));
        AdminRemediationResultView reconcile = remediationService.executeBillingReconcile(
            userId,
            "orig-tx-108",
            "Support triggered App Store transaction reconciliation",
            "rem-billing-rec-001",
            adminId,
            "ROLE_ADMIN"
        );
        assertThat(reconcile.outcome()).isEqualTo("APPLIED");
        assertThat(reconcile.action()).isEqualTo("BILLING_RECONCILE_EXECUTED");
        String entitlementTier = jdbcTemplate.queryForObject(
            "SELECT tier FROM entitlements WHERE user_id = ?",
            String.class,
            userId
        );
        assertThat(entitlementTier).isEqualTo("PRO");
    }

    @Test
    @SuppressWarnings("unchecked")
    void exportsPiiFreeReportsAndDeletesUserScopedRecordsWithoutTouchingOtherUsersOrAuditLogs() {
        UUID userA = UUID.fromString("00000000-0000-0000-0000-00000000B011");
        UUID userB = UUID.fromString("00000000-0000-0000-0000-00000000B012");
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-00000000A011");
        UUID sessionA = UUID.fromString("00000000-0000-0000-0000-00000000C011");
        UUID sessionB = UUID.fromString("00000000-0000-0000-0000-00000000C012");
        seedUserAndSession(userA, sessionA);
        seedUserAndSession(userB, sessionB);

        wrongAnswerReportService.submitLearnerReport(
            userA,
            sessionA,
            "FALSE_VERIFICATION",
            "Verifier marked incomplete root set as valid",
            "war-gdpr-userA-1"
        );
        wrongAnswerReportService.submitLearnerReport(
            userB,
            sessionB,
            "OTHER",
            "Minor formatting glitch",
            "war-gdpr-userB-1"
        );

        rawContentAccessService.createGrant(
            sessionA,
            "SUP-GDPR-1",
            "Investigating userA parse issue under support ticket SUP-GDPR-1",
            15,
            "grant-gdpr-userA-1",
            adminId,
            List.of(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ADMIN_RAW_CONTENT_READ")
            )
        );

        Map<String, Object> exported = privacyContributor.exportUserData(userA);
        assertThat(exported.get("category")).isEqualTo("admin_support");
        assertThat(exported.get("trainingEligible")).isEqualTo(false);
        assertThat(exported.get("reportCount")).isEqualTo(1);
        List<Map<String, Object>> reports = (List<Map<String, Object>>) exported.get("wrongAnswerReports");
        assertThat(reports).hasSize(1);
        assertThat(reports.getFirst())
            .containsEntry("reportReason", "FALSE_VERIFICATION")
            .containsEntry("trainingEligible", false)
            .doesNotContainKeys("payloadHash", "payload_hash", "idempotencyKey", "idempotency_key", "reviewerUserId");

        privacyContributor.deleteUserData(userA, Instant.now());

        Integer userAReports = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM wrong_answer_reports WHERE user_id = ?",
            Integer.class,
            userA
        );
        Integer userAGrants = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM admin_raw_content_access_grants WHERE target_user_id = ?",
            Integer.class,
            userA
        );
        Integer userBReports = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM wrong_answer_reports WHERE user_id = ?",
            Integer.class,
            userB
        );
        Integer auditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM admin_support_audit_logs",
            Integer.class
        );
        assertThat(userAReports).isZero();
        assertThat(userAGrants).isZero();
        assertThat(userBReports).isEqualTo(1);
        assertThat(auditCount).isGreaterThanOrEqualTo(1);
    }

    private void seedUserAndSession(UUID userId, UUID sessionId) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
            "INSERT INTO users (id, status, created_at, updated_at) VALUES (?, 'ACTIVE', ?, ?)",
            userId,
            now,
            now
        );
        jdbcTemplate.update(
            "INSERT INTO problem_sessions (id, user_id, status, input_mode, created_at, updated_at, version) "
                + "VALUES (?, ?, 'CREATED', 'CAMERA', ?, ?, 0)",
            sessionId,
            userId,
            now,
            now
        );
    }

    @TestConfiguration
    static class BillingStubConfig {
        @Bean
        @Primary
        AppStoreSignedDataVerifier testSignedDataVerifier(java.time.Clock clock) {
            return new AppStoreSignedDataVerifier() {
                @Override
                public VerifiedAppStoreTransaction verifyTransaction(String signedTransactionInfo) {
                    String[] parts = signedTransactionInfo.split("\\|", -1);
                    Instant purchasedAt = clock.instant().minusSeconds(60);
                    return new VerifiedAppStoreTransaction(
                        parts[0],
                        parts[1],
                        parts[2],
                        "verifiedai-main",
                        UUID.fromString(parts[3]),
                        AppStoreEnvironment.XCODE,
                        purchasedAt,
                        purchasedAt,
                        Instant.parse(parts[4]),
                        null,
                        purchasedAt,
                        "PURCHASE",
                        "PURCHASED"
                    );
                }

                @Override
                public VerifiedAppStoreRenewalInfo verifyRenewalInfo(String signedRenewalInfo) {
                    return null;
                }

                @Override
                public VerifiedAppStoreNotification verifyNotification(String signedPayload) {
                    throw new UnsupportedOperationException("not used");
                }
            };
        }

        @Bean
        @Primary
        StubGateway stubGateway() {
            return new StubGateway();
        }

        static final class StubGateway implements AppStoreServerGateway {
            private List<AppStoreSubscriptionStatusRecord> statuses = List.of();

            void reset() {
                this.statuses = List.of();
            }

            void setSubscriptionStatuses(List<AppStoreSubscriptionStatusRecord> statuses) {
                this.statuses = List.copyOf(statuses);
            }

            @Override
            public String getTransactionInfo(String transactionId) {
                throw new UnsupportedOperationException("not used");
            }

            @Override
            public List<String> getTransactionHistory(String anyTransactionId) {
                return List.of();
            }

            @Override
            public List<AppStoreSubscriptionStatusRecord> getAllSubscriptionStatuses(String transactionId) {
                return statuses;
            }

            @Override
            public String requestTestNotification() {
                throw new UnsupportedOperationException("not used");
            }

            @Override
            public String getTestNotificationStatus(String testNotificationToken) {
                throw new UnsupportedOperationException("not used");
            }
        }
    }
}
