package com.verifiedai.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.billing.domain.model.AppStoreEnvironment;
import com.verifiedai.billing.domain.model.AppStoreSubscriptionStatus;
import com.verifiedai.billing.domain.model.EntitlementStatus;
import com.verifiedai.billing.domain.model.EntitlementTier;
import com.verifiedai.billing.domain.model.PremiumCapability;
import com.verifiedai.billing.domain.model.VerifiedAppStoreNotification;
import com.verifiedai.billing.domain.model.VerifiedAppStoreRenewalInfo;
import com.verifiedai.billing.domain.model.VerifiedAppStoreTransaction;
import com.verifiedai.billing.domain.port.AppStoreServerGateway;
import com.verifiedai.billing.domain.port.AppStoreSignedDataVerifier;
import com.verifiedai.billing.domain.port.AppStoreSubscriptionStatusRecord;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@Import(AppleBillingEntitlementTransitionTest.TransitionTestConfiguration.class)
@TestPropertySource(properties = {
    "verifiedai.billing.learning-os-tiers-enabled=true",
    "app.billing.apple.purchase-available=true",
    "app.billing.apple.environment=XCODE",
    "app.billing.apple.products[0].internal-plan-id=pro_monthly",
    "app.billing.apple.products[0].app-store-product-id=local_dev_pro_monthly",
    "app.billing.apple.products[0].entitlement-tier=PRO",
    "app.billing.apple.products[0].subscription-group-id=verifiedai-main",
    "app.billing.apple.products[0].billing-period=P1M",
    "app.billing.apple.products[1].internal-plan-id=coach_pro_monthly",
    "app.billing.apple.products[1].app-store-product-id=local_dev_coach_monthly",
    "app.billing.apple.products[1].entitlement-tier=PRO_PLUS",
    "app.billing.apple.products[1].subscription-group-id=verifiedai-main",
    "app.billing.apple.products[1].billing-period=P1M",
    "app.billing.apple.products[2].internal-plan-id=yks_season",
    "app.billing.apple.products[2].app-store-product-id=local_dev_season_6m",
    "app.billing.apple.products[2].entitlement-tier=PRO_PLUS",
    "app.billing.apple.products[2].subscription-group-id=verifiedai-main",
    "app.billing.apple.products[2].billing-period=P6M"
})
final class AppleBillingEntitlementTransitionTest extends PostgresIntegrationTestSupport {

    private static final Instant T0 = Instant.parse("2026-11-20T12:00:00Z");

    @Autowired
    AppleBillingApplicationService appleBilling;

    @Autowired
    EntitlementApplicationService entitlements;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MutableClock mutableClock;

    @Autowired
    MeterRegistry meterRegistry;

    @BeforeEach
    void resetState() {
        mutableClock.set(T0);
        jdbcTemplate.execute("""
            truncate table
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
    void proPurchaseCoachUpgradeAndAuditTrailRespectMetricsAndConstraints() {
        UUID userId = insertUser();
        UUID token = appleBilling.configuration(userId).appAccountToken();
        double initialUpgrades = counterValue("entitlement_upgraded");

        appleBilling.submitTransaction(
            userId,
            tx("tx-pro-1", "orig-1", "local_dev_pro_monthly", token, T0, T0.plus(Duration.ofDays(30)), null, T0),
            "key-pro-1"
        );

        EffectiveEntitlementAccess proAccess = entitlements.access(userId);
        assertThat(proAccess.effectiveTier()).isEqualTo("PRO");
        assertThat(proAccess.packageCode()).isEqualTo("PRO");
        assertThat(proAccess.status()).isEqualTo(EntitlementStatus.ACTIVE);
        assertThat(proAccess.paidAccessActive()).isTrue();
        assertThat(proAccess.learningOsCapabilities()).containsExactly(
            LearningOsCapability.ATTEMPT_VERIFY,
            LearningOsCapability.DETERMINISTIC_TWIN,
            LearningOsCapability.HYBRID_TWIN,
            LearningOsCapability.MISTAKE_PASSPORT_FULL
        );

        Instant t1 = T0.plusSeconds(300);
        mutableClock.set(t1);
        appleBilling.submitTransaction(
            userId,
            tx("tx-coach-2", "orig-1", "local_dev_coach_monthly", token, t1, t1.plus(Duration.ofDays(30)), null, t1),
            "key-coach-2"
        );

        EffectiveEntitlementAccess coachAccess = entitlements.access(userId);
        assertThat(coachAccess.effectiveTier()).isEqualTo("COACH_PRO");
        assertThat(coachAccess.legacyTier()).isEqualTo(EntitlementTier.PRO_PLUS);
        assertThat(coachAccess.packageCode()).isEqualTo("COACH_PRO");
        assertThat(coachAccess.learningOsCapabilities()).containsExactly(LearningOsCapability.values());
        assertThat(counterValue("entitlement_upgraded")).isGreaterThanOrEqualTo(initialUpgrades + 2.0);
        assertThat(countWhere("billing_entitlement_transition_audit", "user_id = '" + userId + "' and training_eligible = false"))
            .isEqualTo(2);
    }

    @Test
    void seasonPassGraceAndBillingRetryEnforceExactClockBoundariesWithoutReadAuditWrites() {
        UUID seasonUser = insertUser();
        UUID seasonToken = appleBilling.configuration(seasonUser).appAccountToken();
        Instant seasonEnd = T0.plus(Duration.ofDays(183));
        appleBilling.submitTransaction(
            seasonUser,
            tx("tx-season-1", "orig-season-1", "local_dev_season_6m", seasonToken, T0, seasonEnd, null, T0),
            "key-season-1"
        );

        EffectiveEntitlementAccess beforeEnd = entitlements.access(seasonUser);
        assertThat(beforeEnd.effectiveTier()).isEqualTo("COACH_PRO");
        assertThat(beforeEnd.packageCode()).isEqualTo("YKS_SEASON_PASS");
        assertThat(beforeEnd.paidAccessActive()).isTrue();

        int auditBeforeRead = countWhere("billing_entitlement_transition_audit", "user_id = '" + seasonUser + "'");
        mutableClock.set(seasonEnd);
        EffectiveEntitlementAccess atEnd = entitlements.access(seasonUser);
        assertThat(atEnd.effectiveTier()).isEqualTo("FREE");
        assertThat(atEnd.packageCode()).isEqualTo("FREE");
        assertThat(atEnd.status()).isEqualTo(EntitlementStatus.EXPIRED);
        assertThat(atEnd.paidAccessActive()).isFalse();
        assertThat(atEnd.capabilities()).containsExactly(PremiumCapability.BASIC_SOLVE);
        assertThat(atEnd.learningOsCapabilities()).containsExactly(
            LearningOsCapability.ATTEMPT_VERIFY,
            LearningOsCapability.DETERMINISTIC_TWIN
        );
        assertThat(countWhere("billing_entitlement_transition_audit", "user_id = '" + seasonUser + "'"))
            .isEqualTo(auditBeforeRead);

        UUID graceUser = insertUser();
        UUID graceToken = appleBilling.configuration(graceUser).appAccountToken();
        Instant subExpiry = T0.plusSeconds(3600);
        Instant graceExpiry = T0.plusSeconds(7200);
        appleBilling.submitTransaction(
            graceUser,
            tx("tx-grace-1", "orig-grace-1", "local_dev_pro_monthly", graceToken, T0, subExpiry, null, T0),
            "key-grace-1"
        );
        mutableClock.set(T0.plusSeconds(3700));
        appleBilling.ingestNotification(notifWithRenewal(
            "notif-grace-1", "DID_FAIL_TO_RENEW", "GRACE_PERIOD", 4, T0.plusSeconds(3700),
            tx("tx-grace-1", "orig-grace-1", "local_dev_pro_monthly", graceToken, T0, subExpiry, null, T0.plusSeconds(3700)),
            renewal("orig-grace-1", "local_dev_pro_monthly", false, graceExpiry, T0.plusSeconds(3700))
        ));

        mutableClock.set(graceExpiry.minusMillis(1));
        EffectiveEntitlementAccess withinGrace = entitlements.access(graceUser);
        assertThat(withinGrace.status()).isEqualTo(EntitlementStatus.GRACE_PERIOD);
        assertThat(withinGrace.effectiveTier()).isEqualTo("PRO");
        assertThat(withinGrace.paidAccessActive()).isTrue();

        mutableClock.set(graceExpiry);
        EffectiveEntitlementAccess atGraceExpiry = entitlements.access(graceUser);
        assertThat(atGraceExpiry.effectiveTier()).isEqualTo("FREE");
        assertThat(atGraceExpiry.paidAccessActive()).isFalse();
        assertThat(atGraceExpiry.capabilities()).containsExactly(PremiumCapability.BASIC_SOLVE);
        assertThat(atGraceExpiry.learningOsCapabilities()).containsExactly(
            LearningOsCapability.ATTEMPT_VERIFY,
            LearningOsCapability.DETERMINISTIC_TWIN
        );

        UUID retryUser = insertUser();
        UUID retryToken = appleBilling.configuration(retryUser).appAccountToken();
        Instant retryExpiry = T0.plusSeconds(3600);
        appleBilling.submitTransaction(
            retryUser,
            tx("tx-retry-1", "orig-retry-1", "local_dev_pro_monthly", retryToken, T0, retryExpiry, null, T0),
            "key-retry-1"
        );
        mutableClock.set(T0.plusSeconds(100));
        appleBilling.ingestNotification(notifWithRenewal(
            "notif-retry-1", "DID_FAIL_TO_RENEW", null, 3, T0.plusSeconds(100),
            tx("tx-retry-1", "orig-retry-1", "local_dev_pro_monthly", retryToken, T0, retryExpiry, null, T0.plusSeconds(100)),
            renewal("orig-retry-1", "local_dev_pro_monthly", true, null, T0.plusSeconds(100))
        ));

        mutableClock.set(retryExpiry.minusMillis(1));
        assertThat(entitlements.access(retryUser).paidAccessActive()).isTrue();
        assertThat(entitlements.access(retryUser).status()).isEqualTo(EntitlementStatus.BILLING_RETRY);

        mutableClock.set(retryExpiry);
        EffectiveEntitlementAccess atRetryExpiry = entitlements.access(retryUser);
        assertThat(atRetryExpiry.paidAccessActive()).isFalse();
        assertThat(atRetryExpiry.effectiveTier()).isEqualTo("FREE");
    }

    @Test
    void refundCannotBeResurrectedByStaleReplayOnlyBySubsequentVerifiedRenewal() {
        UUID userId = insertUser();
        UUID token = appleBilling.configuration(userId).appAccountToken();
        Instant exp1 = T0.plus(Duration.ofDays(30));

        appleBilling.submitTransaction(
            userId,
            tx("tx-r1", "orig-r", "local_dev_pro_monthly", token, T0, exp1, null, T0),
            "key-r1"
        );

        Instant revokeAt = T0.plusSeconds(600);
        mutableClock.set(revokeAt);
        appleBilling.ingestNotification(notif(
            "notif-refund-1", "REFUND", 5, revokeAt,
            tx("tx-r1", "orig-r", "local_dev_pro_monthly", token, T0, exp1, revokeAt, revokeAt)
        ));

        EffectiveEntitlementAccess revoked = entitlements.access(userId);
        assertThat(revoked.status()).isEqualTo(EntitlementStatus.REVOKED);
        assertThat(revoked.effectiveTier()).isEqualTo("FREE");
        assertThat(revoked.paidAccessActive()).isFalse();

        // Duplicate webhook UUID does not add a second audit row.
        appleBilling.ingestNotification(notif(
            "notif-refund-1", "REFUND", 5, revokeAt,
            tx("tx-r1", "orig-r", "local_dev_pro_monthly", token, T0, exp1, revokeAt, revokeAt)
        ));
        assertThat(countWhere("billing_entitlement_transition_audit", "user_id = '" + userId + "' and to_status = 'REVOKED'"))
            .isEqualTo(1);

        // Out-of-order notification with older signedDate is ignored, and stale ACTIVE replay of tx-r1 cannot resurrect access.
        Instant staleAt = T0.plusSeconds(120);
        mutableClock.set(T0.plusSeconds(700));
        appleBilling.ingestNotification(notif(
            "notif-stale-active", "DID_RENEW", 1, staleAt,
            tx("tx-r1", "orig-r", "local_dev_pro_monthly", token, T0, exp1, null, staleAt)
        ));
        assertThat(entitlements.access(userId).effectiveTier()).isEqualTo("FREE");

        // Brand-new verified transaction after refund re-enables paid access cleanly.
        Instant renewAt = T0.plusSeconds(1200);
        Instant exp2 = renewAt.plus(Duration.ofDays(30));
        mutableClock.set(renewAt);
        appleBilling.ingestNotification(notif(
            "notif-renew-2", "SUBSCRIBED", 1, renewAt,
            tx("tx-r2", "orig-r", "local_dev_pro_monthly", token, renewAt, exp2, null, renewAt)
        ));
        EffectiveEntitlementAccess renewed = entitlements.access(userId);
        assertThat(renewed.effectiveTier()).isEqualTo("PRO");
        assertThat(renewed.status()).isEqualTo(EntitlementStatus.ACTIVE);
        assertThat(renewed.paidAccessActive()).isTrue();
    }

    @Test
    void multiSubscriptionFallbackAndCrossUserConflictProtectOwnership() {
        UUID owner = insertUser();
        UUID intruder = insertUser();
        UUID ownerToken = appleBilling.configuration(owner).appAccountToken();
        appleBilling.configuration(intruder);

        Instant exp = T0.plus(Duration.ofDays(30));
        appleBilling.submitTransaction(
            owner,
            tx("tx-multi-pro", "orig-multi-pro", "local_dev_pro_monthly", ownerToken, T0, exp, null, T0),
            "key-multi-pro"
        );
        mutableClock.set(T0.plusSeconds(10));
        appleBilling.submitTransaction(
            owner,
            tx("tx-multi-coach", "orig-multi-coach", "local_dev_coach_monthly", ownerToken, T0.plusSeconds(10), exp, null, T0.plusSeconds(10)),
            "key-multi-coach"
        );
        assertThat(entitlements.access(owner).effectiveTier()).isEqualTo("COACH_PRO");

        Instant revokeAt = T0.plusSeconds(60);
        mutableClock.set(revokeAt);
        appleBilling.ingestNotification(notif(
            "notif-revoke-coach", "REVOKE", 5, revokeAt,
            tx("tx-multi-coach", "orig-multi-coach", "local_dev_coach_monthly", ownerToken, T0.plusSeconds(10), exp, revokeAt, revokeAt)
        ));

        EffectiveEntitlementAccess fallback = entitlements.access(owner);
        assertThat(fallback.effectiveTier()).isEqualTo("PRO");
        assertThat(fallback.status()).isEqualTo(EntitlementStatus.ACTIVE);
        assertThat(fallback.paidAccessActive()).isTrue();

        assertThatThrownBy(() -> appleBilling.submitTransaction(
            intruder,
            tx("tx-multi-pro", "orig-multi-pro", "local_dev_pro_monthly", ownerToken, T0, exp, null, T0),
            "key-intruder-theft"
        ))
            .isInstanceOf(ApiProblemException.class)
            .extracting(ex -> ((ApiProblemException) ex).code())
            .isEqualTo(ApiErrorCode.PURCHASE_ALREADY_OWNED_BY_OTHER_ACCOUNT);
        assertThat(entitlements.access(intruder).effectiveTier()).isEqualTo("FREE");
        assertThat(entitlements.access(owner).effectiveTier()).isEqualTo("PRO");
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into users (id, status, created_at, updated_at) values (?, 'ACTIVE', ?, ?)",
            id, Timestamp.from(T0), Timestamp.from(T0)
        );
        return id;
    }

    private Integer countWhere(String table, String predicate) {
        return jdbcTemplate.queryForObject("select count(*) from " + table + " where " + predicate, Integer.class);
    }

    private double counterValue(String name) {
        return meterRegistry.find(name).counters().stream().mapToDouble(c -> c.count()).sum();
    }

    private static String tx(
        String transactionId,
        String originalTransactionId,
        String productId,
        UUID appAccountToken,
        Instant purchaseDate,
        Instant expiresDate,
        Instant revocationDate,
        Instant signedDate
    ) {
        return String.join("|",
            transactionId,
            originalTransactionId,
            productId,
            appAccountToken.toString(),
            purchaseDate.toString(),
            expiresDate.toString(),
            revocationDate == null ? "" : revocationDate.toString(),
            signedDate.toString()
        );
    }

    private static String renewal(
        String originalTransactionId,
        String productId,
        boolean inBillingRetry,
        Instant graceExpires,
        Instant signedDate
    ) {
        return String.join("|",
            originalTransactionId,
            productId,
            Boolean.toString(inBillingRetry),
            graceExpires == null ? "" : graceExpires.toString(),
            signedDate.toString()
        );
    }

    private static String notif(String uuid, String type, int appStoreStatus, Instant signedDate, String txPayload) {
        return notifWithRenewal(uuid, type, null, appStoreStatus, signedDate, txPayload, "");
    }

    private static String notifWithRenewal(
        String uuid,
        String type,
        String subtype,
        int appStoreStatus,
        Instant signedDate,
        String txPayload,
        String renewalPayload
    ) {
        return String.join("~",
            uuid,
            type,
            subtype == null ? "" : subtype,
            Integer.toString(appStoreStatus),
            signedDate.toString(),
            txPayload,
            renewalPayload == null ? "" : renewalPayload
        );
    }

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current = new AtomicReference<>(T0);

        void set(Instant instant) {
            current.set(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }

    @TestConfiguration
    static class TransitionTestConfiguration {
        @Bean
        @Primary
        MutableClock testMutableClock() {
            return new MutableClock();
        }

        @Bean
        @Primary
        AppStoreSignedDataVerifier testSignedDataVerifier() {
            return new AppStoreSignedDataVerifier() {
                @Override
                public VerifiedAppStoreTransaction verifyTransaction(String signedTransactionInfo) {
                    return parseTx(signedTransactionInfo);
                }

                @Override
                public VerifiedAppStoreRenewalInfo verifyRenewalInfo(String signedRenewalInfo) {
                    if (signedRenewalInfo == null || signedRenewalInfo.isBlank()) {
                        return null;
                    }
                    String[] p = signedRenewalInfo.split("\\|", -1);
                    return new VerifiedAppStoreRenewalInfo(
                        p[0],
                        p[1],
                        p[1],
                        true,
                        Boolean.parseBoolean(p[2]),
                        p[3].isBlank() ? null : Instant.parse(p[3]),
                        null,
                        Instant.parse(p[4])
                    );
                }

                @Override
                public VerifiedAppStoreNotification verifyNotification(String signedPayload) {
                    String[] parts = signedPayload.split("~", 7);
                    return new VerifiedAppStoreNotification(
                        parts[0],
                        parts[1],
                        parts[2].isBlank() ? null : parts[2],
                        AppStoreEnvironment.XCODE,
                        Integer.parseInt(parts[3]),
                        Instant.parse(parts[4]),
                        parseTx(parts[5]),
                        verifyRenewalInfo(parts[6])
                    );
                }

                private VerifiedAppStoreTransaction parseTx(String payload) {
                    String[] p = payload.split("\\|", -1);
                    Instant purchaseDate = Instant.parse(p[4]);
                    return new VerifiedAppStoreTransaction(
                        p[0],
                        p[1],
                        p[2],
                        "verifiedai-main",
                        UUID.fromString(p[3]),
                        AppStoreEnvironment.XCODE,
                        purchaseDate,
                        purchaseDate,
                        Instant.parse(p[5]),
                        p[6].isBlank() ? null : Instant.parse(p[6]),
                        Instant.parse(p[7]),
                        "PURCHASE",
                        "PURCHASED"
                    );
                }
            };
        }

        @Bean
        @Primary
        AppStoreServerGateway testServerGateway() {
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
