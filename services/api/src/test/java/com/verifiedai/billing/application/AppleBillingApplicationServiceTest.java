package com.verifiedai.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.billing.domain.model.AppStoreEnvironment;
import com.verifiedai.billing.domain.model.AppStoreSubscriptionStatus;
import com.verifiedai.billing.domain.model.EntitlementSource;
import com.verifiedai.billing.domain.model.EntitlementStatus;
import com.verifiedai.billing.domain.model.EntitlementTier;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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

@Import(AppleBillingApplicationServiceTest.BillingTestConfiguration.class)
@TestPropertySource(properties = {
    "app.billing.apple.purchase-available=true",
    "app.billing.apple.environment=XCODE",
    "app.billing.apple.products[0].internal-plan-id=pro_monthly",
    "app.billing.apple.products[0].app-store-product-id=com.verifiedai.pro.monthly",
    "app.billing.apple.products[0].entitlement-tier=PRO",
    "app.billing.apple.products[0].subscription-group-id=verifiedai-main",
    "app.billing.apple.products[0].billing-period=P1M"
})
final class AppleBillingApplicationServiceTest extends PostgresIntegrationTestSupport {
    private static final Instant NOW = Instant.parse("2026-08-08T00:00:00Z");
    private static final Instant FUTURE_EXPIRY = Instant.parse("2026-09-08T00:00:00Z");
    private static final Instant PAST_EXPIRY = Instant.parse("2026-08-01T00:00:00Z");

    @Autowired
    AppleBillingApplicationService appleBillingApplicationService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    FakeAppStoreServerGateway appStoreServerGateway;

    @BeforeEach
    void cleanTables() {
        appStoreServerGateway.reset();
        jdbcTemplate.execute("""
            truncate table
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
    void configurationIssuesStableAppAccountTokenFromBackend() {
        UUID userId = insertUser();

        AppleBillingConfigurationResult first = appleBillingApplicationService.configuration(userId);
        AppleBillingConfigurationResult second = appleBillingApplicationService.configuration(userId);

        assertThat(first.appAccountToken()).isEqualTo(second.appAccountToken());
        assertThat(first.purchaseAvailable()).isTrue();
        assertThat(first.products()).extracting(AppleBillingProductResult::appStoreProductId)
            .containsExactly("com.verifiedai.pro.monthly");
        assertThat(count("commerce_account_tokens")).isEqualTo(1);
    }

    @Test
    void verifiedPurchaseEvidencePromotesEntitlementThroughBackendPolicy() {
        UUID userId = insertUser();
        UUID appAccountToken = appleBillingApplicationService.configuration(userId).appAccountToken();

        ApplePurchaseEvidenceResult result = appleBillingApplicationService.submitTransaction(
            userId,
            transaction("1000001", "1000000", appAccountToken, FUTURE_EXPIRY, null),
            "purchase-1000001"
        );

        assertThat(result.subscriptionStatus()).isEqualTo(AppStoreSubscriptionStatus.ACTIVE);
        assertThat(result.entitlement().tier()).isEqualTo(EntitlementTier.PRO);
        assertThat(result.entitlement().source()).isEqualTo(EntitlementSource.APP_STORE_SUBSCRIPTION);
        assertThat(result.entitlement().status()).isEqualTo(EntitlementStatus.ACTIVE);
        assertThat(count("app_store_transactions")).isEqualTo(1);
        assertThat(count("app_store_subscriptions")).isEqualTo(1);
        assertThat(count("billing_events")).isEqualTo(1);
    }

    @Test
    void transactionOwnedByAnotherAppAccountTokenIsRejected() {
        UUID ownerUserId = insertUser();
        UUID otherUserId = insertUser();
        UUID ownerToken = appleBillingApplicationService.configuration(ownerUserId).appAccountToken();
        appleBillingApplicationService.configuration(otherUserId);

        appleBillingApplicationService.submitTransaction(
            ownerUserId,
            transaction("1000002", "1000000", ownerToken, FUTURE_EXPIRY, null),
            "purchase-1000002-owner"
        );

        assertThatThrownBy(() -> appleBillingApplicationService.submitTransaction(
            otherUserId,
            transaction("1000002", "1000000", ownerToken, FUTURE_EXPIRY, null),
            "purchase-1000002-other"
        ))
            .isInstanceOf(ApiProblemException.class)
            .extracting(exception -> ((ApiProblemException) exception).code())
            .isEqualTo(ApiErrorCode.PURCHASE_ALREADY_OWNED_BY_OTHER_ACCOUNT);
    }

    @Test
    void expiredNotificationDowngradesPaidEntitlementToDefaultFree() {
        UUID userId = insertUser();
        UUID appAccountToken = appleBillingApplicationService.configuration(userId).appAccountToken();
        appleBillingApplicationService.submitTransaction(
            userId,
            transaction("1000003", "1000000", appAccountToken, FUTURE_EXPIRY, null),
            "purchase-1000003"
        );

        AppleNotificationIngestionResult result = appleBillingApplicationService.ingestNotification(
            notification(
                "notification-1000003-expired",
                "EXPIRED",
                2,
                transaction("1000004", "1000000", appAccountToken, PAST_EXPIRY, null)
            )
        );

        assertThat(result.subscriptionStatus()).isEqualTo(AppStoreSubscriptionStatus.EXPIRED);
        EntitlementResult current = result.entitlement();
        assertThat(current.tier()).isEqualTo(EntitlementTier.FREE);
        assertThat(current.source()).isEqualTo(EntitlementSource.DEFAULT_FREE);
        assertThat(current.status()).isEqualTo(EntitlementStatus.ACTIVE);
        assertThat(countWhere("app_store_notifications", "processing_status = 'PROCESSED'")).isEqualTo(1);
    }

    @Test
    void reconciliationUsesAllSubscriptionStatusesBeforeTransactionHistory() {
        UUID userId = insertUser();
        UUID appAccountToken = appleBillingApplicationService.configuration(userId).appAccountToken();
        appStoreServerGateway.subscriptionStatuses(List.of(new AppStoreSubscriptionStatusRecord(
            "1000000",
            1,
            transaction("1000005", "1000000", appAccountToken, FUTURE_EXPIRY, null),
            ""
        )));

        ApplePurchaseEvidenceResult result = appleBillingApplicationService.reconcileFromTransaction(userId, "1000000");

        assertThat(result.subscriptionStatus()).isEqualTo(AppStoreSubscriptionStatus.ACTIVE);
        assertThat(result.entitlement().tier()).isEqualTo(EntitlementTier.PRO);
        assertThat(appStoreServerGateway.transactionHistoryRequested()).isFalse();
        assertThat(count("app_store_transactions")).isEqualTo(1);
        assertThat(count("app_store_subscriptions")).isEqualTo(1);
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into users (id, status, created_at, updated_at) values (?, 'ACTIVE', ?, ?)",
            userId,
            Timestamp.from(NOW),
            Timestamp.from(NOW)
        );
        return userId;
    }

    private static String transaction(
        String transactionId,
        String originalTransactionId,
        UUID appAccountToken,
        Instant expiresAt,
        Instant revokedAt
    ) {
        return String.join(
            "|",
            transactionId,
            originalTransactionId,
            "com.verifiedai.pro.monthly",
            appAccountToken.toString(),
            expiresAt.toString(),
            revokedAt == null ? "" : revokedAt.toString()
        );
    }

    private static String notification(String uuid, String type, int status, String transaction) {
        return uuid + "~" + type + "~" + status + "~" + transaction;
    }

    private Integer count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    }

    private Integer countWhere(String table, String predicate) {
        return jdbcTemplate.queryForObject("select count(*) from " + table + " where " + predicate, Integer.class);
    }

    @TestConfiguration
    static class BillingTestConfiguration {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        AppStoreSignedDataVerifier appStoreSignedDataVerifier() {
            return new AppStoreSignedDataVerifier() {
                @Override
                public VerifiedAppStoreTransaction verifyTransaction(String signedTransactionInfo) {
                    return parseTransaction(signedTransactionInfo);
                }

                @Override
                public VerifiedAppStoreRenewalInfo verifyRenewalInfo(String signedRenewalInfo) {
                    return null;
                }

                @Override
                public VerifiedAppStoreNotification verifyNotification(String signedPayload) {
                    String[] parts = signedPayload.split("~", 4);
                    return new VerifiedAppStoreNotification(
                        parts[0],
                        parts[1],
                        null,
                        AppStoreEnvironment.XCODE,
                        Integer.parseInt(parts[2]),
                        NOW,
                        parseTransaction(parts[3]),
                        null
                    );
                }

                private VerifiedAppStoreTransaction parseTransaction(String payload) {
                    String[] parts = payload.split("\\|", -1);
                    return new VerifiedAppStoreTransaction(
                        parts[0],
                        parts[1],
                        parts[2],
                        "verifiedai-main",
                        UUID.fromString(parts[3]),
                        AppStoreEnvironment.XCODE,
                        NOW,
                        NOW,
                        Instant.parse(parts[4]),
                        parts[5].isBlank() ? null : Instant.parse(parts[5]),
                        NOW,
                        "PURCHASE",
                        "PURCHASED"
                    );
                }
            };
        }

        @Bean
        @Primary
        FakeAppStoreServerGateway appStoreServerGateway() {
            return new FakeAppStoreServerGateway();
        }
    }

    static final class FakeAppStoreServerGateway implements AppStoreServerGateway {
        private List<AppStoreSubscriptionStatusRecord> subscriptionStatuses = List.of();
        private boolean transactionHistoryRequested;

        void reset() {
            subscriptionStatuses = List.of();
            transactionHistoryRequested = false;
        }

        void subscriptionStatuses(List<AppStoreSubscriptionStatusRecord> subscriptionStatuses) {
            this.subscriptionStatuses = List.copyOf(subscriptionStatuses);
        }

        boolean transactionHistoryRequested() {
            return transactionHistoryRequested;
        }

        @Override
        public String getTransactionInfo(String transactionId) {
            throw new UnsupportedOperationException("not used by this test");
        }

        @Override
        public List<String> getTransactionHistory(String anyTransactionId) {
            transactionHistoryRequested = true;
            return List.of();
        }

        @Override
        public List<AppStoreSubscriptionStatusRecord> getAllSubscriptionStatuses(String originalTransactionId) {
            return subscriptionStatuses;
        }

        @Override
        public String requestTestNotification() {
            throw new UnsupportedOperationException("not used by this test");
        }

        @Override
        public String getTestNotificationStatus(String testNotificationToken) {
            throw new UnsupportedOperationException("not used by this test");
        }
    }
}
