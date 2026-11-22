package com.verifiedai.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.billing.domain.model.AppStoreEnvironment;
import com.verifiedai.billing.domain.model.EntitlementStatus;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@Import(EntitlementDowngradeLifecycleTest.LifecycleTestConfiguration.class)
@TestPropertySource(properties = {
    "verifiedai.billing.learning-os-tiers-enabled=true",
    "app.billing.apple.purchase-available=true",
    "app.billing.apple.environment=XCODE",
    "app.billing.apple.products[0].internal-plan-id=pro_monthly",
    "app.billing.apple.products[0].app-store-product-id=local_dev_pro_monthly",
    "app.billing.apple.products[0].entitlement-tier=PRO",
    "app.billing.apple.products[0].subscription-group-id=verifiedai-main",
    "app.billing.apple.products[0].billing-period=P1M"
})
final class EntitlementDowngradeLifecycleTest extends PostgresIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");

    @Autowired
    AppleBillingApplicationService appleBilling;

    @Autowired
    EntitlementApplicationService entitlements;

    @Autowired
    BillingLifecycleContributor lifecycle;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("""
            truncate table
                learner_mistake_dna_profiles,
                learner_skill_mastery,
                billing_entitlement_transition_audit,
                billing_events,
                app_store_notifications,
                app_store_subscriptions,
                app_store_transactions,
                commerce_account_tokens,
                entitlements,
                users
            cascade
            """);
    }

    @Test
    void downgradePreservesMistakeDnaAndSkillMasteryRowsAndExposesSummaryHistory() {
        UUID userId = insertUser();
        jdbcTemplate.update("""
            insert into learner_mistake_dna_profiles
                (user_id, mistake_code, skill_id, taxonomy_version, category_version, policy_version,
                 decayed_error_count, decayed_exposure_count, smoothed_propensity, recent_error_count,
                 recurrence_state, last_occurred_at, as_of, training_eligible)
            values (?, 'INEQ_NEGATIVE_MULT_NO_FLIP', 'MATH.ALG.INEQ', 'attempt-taxonomy-v2',
                    'mistake-category-v1', 'mistake-dna-v1', 2.0, 4.0, 0.5, 2,
                    'RECURRING_PATTERN', ?, ?, false)
            """, userId, Timestamp.from(NOW), Timestamp.from(NOW));
        jdbcTemplate.update("""
            insert into learner_skill_mastery
                (user_id, skill_id, mastery_version, score, half_life_days, evidence_count,
                 confidence, last_assessed_at, training_eligible)
            values (?, 'MATH.ALG.INEQ', 'bkt-lite-v1', 0.72, 14.0, 3, 0.80, ?, false)
            """, userId, Timestamp.from(NOW));

        UUID token = appleBilling.configuration(userId).appAccountToken();
        appleBilling.submitTransaction(
            userId,
            tx("tx-life-1", "orig-life-1", token, NOW.plus(Duration.ofDays(30))),
            "key-life-1"
        );
        assertThat(entitlements.access(userId).effectiveTier()).isEqualTo("PRO");

        appleBilling.ingestNotification(notif(
            "notif-life-expired",
            "EXPIRED",
            2,
            tx("tx-life-1", "orig-life-1", token, NOW.minusSeconds(10))
        ));

        EffectiveEntitlementAccess downgraded = entitlements.access(userId);
        assertThat(downgraded.effectiveTier()).isEqualTo("FREE");
        assertThat(downgraded.status()).isEqualTo(EntitlementStatus.EXPIRED);
        assertThat(downgraded.historyAccess()).containsExactlyInAnyOrderEntriesOf(Map.of(
            "mistakePassport", "SUMMARY",
            "skillMastery", "SUMMARY",
            "priorExams", "SUMMARY"
        ));
        assertThat(countWhere("learner_mistake_dna_profiles", "user_id = '" + userId + "'")).isEqualTo(1);
        assertThat(countWhere("learner_skill_mastery", "user_id = '" + userId + "'")).isEqualTo(1);
    }

    @Test
    void gdprExportAndDeleteIsolateTwoUsersAndPreserveMinimizedTransactions() {
        UUID owner = insertUser();
        UUID other = insertUser();
        UUID ownerToken = appleBilling.configuration(owner).appAccountToken();
        UUID otherToken = appleBilling.configuration(other).appAccountToken();

        appleBilling.submitTransaction(
            owner,
            tx("tx-owner-1", "orig-owner-1", ownerToken, NOW.plus(Duration.ofDays(30))),
            "key-owner-1"
        );
        appleBilling.submitTransaction(
            other,
            tx("tx-other-1", "orig-other-1", otherToken, NOW.plus(Duration.ofDays(30))),
            "key-other-1"
        );

        Map<String, Object> exportedOwner = lifecycle.exportUserData(owner);
        assertThat(exportedOwner.get("transitions")).asList().hasSize(1);
        assertThat(exportedOwner.toString())
            .contains("tx-owner-1", "PRO")
            .doesNotContain("tx-other-1", ownerToken.toString(), otherToken.toString());

        assertThat(lifecycle.exportUserData(other).toString())
            .contains("tx-other-1")
            .doesNotContain("tx-owner-1");

        lifecycle.deleteUserData(owner, NOW);
        lifecycle.deleteUserData(owner, NOW.plusSeconds(1));

        assertThat(countWhere("billing_entitlement_transition_audit", "user_id = '" + owner + "'")).isZero();
        assertThat(countWhere("commerce_account_tokens", "user_id = '" + owner + "'")).isZero();
        assertThat(countWhere("app_store_transactions", "user_id = '" + owner + "'")).isEqualTo(1);

        assertThat(countWhere("billing_entitlement_transition_audit", "user_id = '" + other + "'")).isEqualTo(1);
        assertThat(countWhere("commerce_account_tokens", "user_id = '" + other + "'")).isEqualTo(1);
        assertThat(entitlements.access(other).effectiveTier()).isEqualTo("PRO");
    }

    @Test
    void inactiveOrDeletedUserReceivesAccountNotActive() {
        UUID userId = insertUser();
        jdbcTemplate.update("update users set status = 'DELETED' where id = ?", userId);

        assertThatThrownBy(() -> entitlements.access(userId))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> {
                ApiProblemException apiEx = (ApiProblemException) ex;
                assertThat(apiEx.status().value()).isEqualTo(403);
                assertThat(apiEx.code()).isEqualTo(ApiErrorCode.ACCOUNT_NOT_ACTIVE);
            });
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into users (id, status, created_at, updated_at) values (?, 'ACTIVE', ?, ?)",
            id, Timestamp.from(NOW), Timestamp.from(NOW)
        );
        return id;
    }

    private Integer countWhere(String table, String predicate) {
        return jdbcTemplate.queryForObject("select count(*) from " + table + " where " + predicate, Integer.class);
    }

    private static String tx(String txId, String origId, UUID token, Instant expiresAt) {
        return String.join("|", txId, origId, "local_dev_pro_monthly", token.toString(), expiresAt.toString());
    }

    private static String notif(String uuid, String type, int status, String txPayload) {
        return uuid + "~" + type + "~" + status + "~" + txPayload;
    }

    @TestConfiguration
    static class LifecycleTestConfiguration {
        @Bean
        @Primary
        Clock testLifecycleClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        AppStoreSignedDataVerifier testLifecycleVerifier() {
            return new AppStoreSignedDataVerifier() {
                @Override
                public VerifiedAppStoreTransaction verifyTransaction(String signedTransactionInfo) {
                    return parseTx(signedTransactionInfo);
                }

                @Override
                public VerifiedAppStoreRenewalInfo verifyRenewalInfo(String signedRenewalInfo) {
                    return null;
                }

                @Override
                public VerifiedAppStoreNotification verifyNotification(String signedPayload) {
                    String[] p = signedPayload.split("~", 4);
                    return new VerifiedAppStoreNotification(
                        p[0],
                        p[1],
                        null,
                        AppStoreEnvironment.XCODE,
                        Integer.parseInt(p[2]),
                        NOW.plusSeconds(60),
                        parseTx(p[3]),
                        null
                    );
                }

                private VerifiedAppStoreTransaction parseTx(String payload) {
                    String[] p = payload.split("\\|", -1);
                    return new VerifiedAppStoreTransaction(
                        p[0],
                        p[1],
                        p[2],
                        "verifiedai-main",
                        UUID.fromString(p[3]),
                        AppStoreEnvironment.XCODE,
                        NOW,
                        NOW,
                        Instant.parse(p[4]),
                        null,
                        NOW.plusSeconds(60),
                        "PURCHASE",
                        "PURCHASED"
                    );
                }
            };
        }

        @Bean
        @Primary
        AppStoreServerGateway testLifecycleGateway() {
            return new AppStoreServerGateway() {
                @Override
                public String getTransactionInfo(String transactionId) {
                    return "";
                }

                @Override
                public List<String> getTransactionHistory(String anyTransactionId) {
                    return List.of();
                }

                @Override
                public List<AppStoreSubscriptionStatusRecord> getAllSubscriptionStatuses(String originalTransactionId) {
                    return List.of();
                }

                @Override
                public String requestTestNotification() {
                    return "";
                }

                @Override
                public String getTestNotificationStatus(String testNotificationToken) {
                    return "";
                }
            };
        }
    }
}
