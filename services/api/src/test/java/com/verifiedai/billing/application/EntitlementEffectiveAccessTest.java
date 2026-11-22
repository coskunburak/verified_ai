package com.verifiedai.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.verifiedai.billing.domain.model.AppStoreEnvironment;
import com.verifiedai.billing.domain.model.AppStoreSubscriptionStatus;
import com.verifiedai.billing.domain.model.EntitlementStatus;
import com.verifiedai.billing.domain.model.EntitlementTier;
import com.verifiedai.billing.infrastructure.configuration.AppleBillingProperties;
import com.verifiedai.billing.infrastructure.persistence.AppStoreBillingRepository;
import com.verifiedai.billing.infrastructure.persistence.AppStoreBillingRepository.SubscriptionProjection;
import com.verifiedai.billing.infrastructure.persistence.EntitlementJpaEntity;
import com.verifiedai.billing.infrastructure.persistence.EntitlementJpaRepository;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

final class EntitlementEffectiveAccessTest {

    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");

    private EntitlementJpaRepository entitlementRepository;
    private JdbcTemplate jdbcTemplate;
    private AppStoreBillingRepository store;
    private SimpleMeterRegistry meterRegistry;
    private AppleProductCatalog catalog;
    private UUID userId;

    @BeforeEach
    void setUp() {
        entitlementRepository = mock(EntitlementJpaRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        store = mock(AppStoreBillingRepository.class);
        meterRegistry = new SimpleMeterRegistry();
        userId = UUID.randomUUID();
        catalog = new AppleProductCatalog(new AppleBillingProperties(
            true,
            true,
            AppStoreEnvironment.XCODE,
            "com.verifiedai.app",
            null,
            null,
            null,
            null,
            List.of(),
            List.of(
                new AppleBillingProperties.Product(
                    "pro_monthly", "local_dev_pro", EntitlementTier.PRO, "group", "P1M"
                ),
                new AppleBillingProperties.Product(
                    "coach_monthly", "local_dev_coach", EntitlementTier.PRO_PLUS, "group", "P1M"
                ),
                new AppleBillingProperties.Product(
                    "yks_season", "local_dev_season", EntitlementTier.PRO_PLUS, "group", "P6M"
                )
            )
        ));
        when(store.userActive(userId)).thenReturn(true);
    }

    private EntitlementApplicationService service(String flag) {
        return new EntitlementApplicationService(
            entitlementRepository,
            Clock.fixed(NOW, ZoneOffset.UTC),
            jdbcTemplate,
            new EntitlementMetrics(meterRegistry),
            catalog,
            store,
            flag
        );
    }

    @Test
    void flagOffAndMalformedValuesDisableNewGuardsSafely() {
        for (String flag : new String[]{"false", "yes", "1", ""}) {
            EntitlementApplicationService disabled = service(flag);
            CapabilityDecision decision = disabled.evaluate(userId, LearningOsCapability.TIMED_MOCK_SESSION);
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.featureEnabled()).isFalse();
            assertThat(decision.denyReason()).isEqualTo("FEATURE_DISABLED");
            assertThat(disabled.check(userId, LearningOsCapability.HYBRID_TWIN)).isTrue();
        }
    }

    @Test
    void evaluatesGraceRetryExpiredAndSeasonBoundariesWithMetrics() {
        EntitlementApplicationService enabled = service("true");

        when(store.subscriptions(userId)).thenReturn(List.of(new SubscriptionProjection(
            "local_dev_coach",
            AppStoreSubscriptionStatus.GRACE_PERIOD,
            NOW.minusSeconds(3600),
            "orig-grace",
            AppStoreEnvironment.XCODE,
            NOW.plusMillis(1),
            NOW.minusSeconds(7200),
            false
        )));
        CapabilityDecision graceValid = enabled.evaluate(userId, LearningOsCapability.TIMED_MOCK_SESSION);
        assertThat(graceValid.allowed()).isTrue();
        assertThat(graceValid.effectiveTier()).isEqualTo("COACH_PRO");
        assertThat(graceValid.denyReason()).isEqualTo("NONE");

        when(store.subscriptions(userId)).thenReturn(List.of(new SubscriptionProjection(
            "local_dev_coach",
            AppStoreSubscriptionStatus.BILLING_RETRY,
            NOW.minusSeconds(1),
            "orig-retry-expired",
            AppStoreEnvironment.XCODE,
            null,
            NOW.minusSeconds(7200),
            false
        )));
        CapabilityDecision retryExpired = enabled.evaluate(userId, LearningOsCapability.HYBRID_TWIN);
        assertThat(retryExpired.allowed()).isFalse();
        assertThat(retryExpired.effectiveTier()).isEqualTo("FREE");
        assertThat(retryExpired.denyReason()).isEqualTo("EXPIRED");

        var counter = meterRegistry.find("entitlement_checked")
            .tag("capability", "HYBRID_TWIN")
            .tag("effective_tier", "FREE")
            .tag("outcome", "DENY")
            .tag("deny_reason", "EXPIRED")
            .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
        assertThat(counter.getId().getTags().stream().map(t -> t.getKey()))
            .containsExactlyInAnyOrder("capability", "effective_tier", "outcome", "deny_reason");

        assertThatThrownBy(() -> enabled.require(userId, LearningOsCapability.MICRO_LESSON))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> {
                ApiProblemException apiEx = (ApiProblemException) ex;
                assertThat(apiEx.status().value()).isEqualTo(403);
                assertThat(apiEx.code()).isEqualTo(ApiErrorCode.ENTITLEMENT_REQUIRED);
                assertThat(apiEx.details()).containsEntry("recoverable", true);
                assertThat(apiEx.details()).containsEntry("userAction", "UPGRADE");
            });
    }

    @Test
    void picksHighestValidSubscriptionOverRevokedSubscriptionAndRejectsDeletedUsers() {
        EntitlementApplicationService enabled = service("true");
        when(store.subscriptions(userId)).thenReturn(List.of(
            new SubscriptionProjection(
                "local_dev_coach",
                AppStoreSubscriptionStatus.REVOKED,
                NOW.plusSeconds(3600),
                "orig-revoked",
                AppStoreEnvironment.XCODE,
                null,
                NOW.minusSeconds(7200),
                false
            ),
            new SubscriptionProjection(
                "local_dev_pro",
                AppStoreSubscriptionStatus.ACTIVE,
                NOW.plusSeconds(3600),
                "orig-pro",
                AppStoreEnvironment.XCODE,
                null,
                NOW.minusSeconds(3600),
                false
            )
        ));

        EffectiveEntitlementAccess access = enabled.access(userId);
        assertThat(access.effectiveTier()).isEqualTo("PRO");
        assertThat(access.packageCode()).isEqualTo("PRO");
        assertThat(access.paidAccessActive()).isTrue();
        assertThat(enabled.check(userId, LearningOsCapability.HYBRID_TWIN)).isTrue();
        assertThat(enabled.check(userId, LearningOsCapability.TIMED_MOCK_SESSION)).isFalse();

        when(store.userActive(userId)).thenReturn(false);
        assertThatThrownBy(() -> enabled.access(userId))
            .isInstanceOf(ApiProblemException.class)
            .extracting(ex -> ((ApiProblemException) ex).code())
            .isEqualTo(ApiErrorCode.ACCOUNT_NOT_ACTIVE);
    }

    @Test
    void billingRepositoryFailureThrows503WithVerificationUnavailableMetric() {
        EntitlementApplicationService enabled = service("true");
        when(store.userActive(userId)).thenThrow(new DataAccessResourceFailureException("db unreachable"));

        assertThatThrownBy(() -> enabled.evaluate(userId, LearningOsCapability.HYBRID_TWIN))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> {
                ApiProblemException apiEx = (ApiProblemException) ex;
                assertThat(apiEx.status().value()).isEqualTo(503);
                assertThat(apiEx.code()).isEqualTo(ApiErrorCode.ENTITLEMENT_SYNC_FAILED);
                assertThat(apiEx.details()).containsEntry("userAction", "RETRY");
            });

        var metric = meterRegistry.find("entitlement_checked")
            .tag("capability", "HYBRID_TWIN")
            .tag("effective_tier", "UNKNOWN")
            .tag("outcome", "DENY")
            .tag("deny_reason", "VERIFICATION_UNAVAILABLE")
            .counter();
        assertThat(metric).isNotNull();
        assertThat(metric.count()).isEqualTo(1.0);
    }

    @Test
    void currentAiTierUsesEffectivePaidWindowAndPromotionalFallback() {
        EntitlementApplicationService enabled = service("true");
        EntitlementJpaEntity promo = EntitlementJpaEntity.defaultFree(userId, NOW.minusSeconds(60));
        promo.applyAppStoreSubscription(
            EntitlementTier.PRO_PLUS,
            EntitlementStatus.ACTIVE,
            NOW.minusSeconds(60),
            NOW.plusSeconds(3600),
            "orig-1",
            AppStoreEnvironment.XCODE,
            NOW.minusSeconds(60)
        );
        when(entitlementRepository.findByUserId(userId)).thenReturn(Optional.of(promo));
        when(store.subscriptions(userId)).thenReturn(List.of(new SubscriptionProjection(
            "local_dev_coach",
            AppStoreSubscriptionStatus.REVOKED,
            NOW.plusSeconds(3600),
            "orig-1",
            AppStoreEnvironment.XCODE,
            null,
            NOW.minusSeconds(60),
            false
        )));

        assertThat(enabled.currentAiTier(userId)).isEqualTo("FREE");
    }
}
