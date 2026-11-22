package com.verifiedai.billing.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.verifiedai.billing.domain.model.EntitlementSource;
import com.verifiedai.billing.domain.model.EntitlementStatus;
import com.verifiedai.billing.domain.model.EntitlementTier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

final class FairUsePolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-22T15:30:00Z");

    @Test
    void resolvesCanonicalLimitsCostCapsAndUtcResetsAcrossAllFourTiers() {
        FairUsePolicy policy = new FairUsePolicy(Clock.fixed(NOW, ZoneOffset.UTC));

        var freeAccess = LearningOsTierCapabilityPolicy.project(
            EntitlementTier.FREE, EntitlementStatus.ACTIVE, EntitlementSource.DEFAULT_FREE,
            null, null, false, true, NOW
        );
        var freePolicy = policy.resolve(freeAccess);
        assertThat(freePolicy.tierCode()).isEqualTo("FREE");
        assertThat(freePolicy.periodType()).isEqualTo("DAILY");
        assertThat(freePolicy.periodKey()).isEqualTo("2026-11-22");
        assertThat(freePolicy.monthlyPeriodKey()).isEqualTo("2026-11");
        assertThat(freePolicy.aiVerificationsLimit()).isEqualTo(3);
        assertThat(freePolicy.hybridTwinsLimit()).isEqualTo(1);
        assertThat(freePolicy.aiCostCapMicros()).isEqualTo(80_000L);
        assertThat(freePolicy.resetsAt()).isEqualTo(Instant.parse("2026-11-23T00:00:00Z"));

        var proAccess = LearningOsTierCapabilityPolicy.project(
            EntitlementTier.PRO, EntitlementStatus.ACTIVE, EntitlementSource.APP_STORE_SUBSCRIPTION,
            NOW.plusSeconds(86400), null, false, true, NOW
        );
        var proPolicy = policy.resolve(proAccess);
        assertThat(proPolicy.tierCode()).isEqualTo("PRO");
        assertThat(proPolicy.periodType()).isEqualTo("MONTHLY");
        assertThat(proPolicy.periodKey()).isEqualTo("2026-11");
        assertThat(proPolicy.aiVerificationsLimit()).isEqualTo(150);
        assertThat(proPolicy.hybridTwinsLimit()).isEqualTo(150);
        assertThat(proPolicy.aiCostCapMicros()).isEqualTo(2_500_000L);
        assertThat(proPolicy.resetsAt()).isEqualTo(Instant.parse("2026-12-01T00:00:00Z"));

        var coachAccess = LearningOsTierCapabilityPolicy.project(
            EntitlementTier.PRO_PLUS, EntitlementStatus.ACTIVE, EntitlementSource.APP_STORE_SUBSCRIPTION,
            NOW.plusSeconds(86400), null, false, true, NOW
        );
        var coachPolicy = policy.resolve(coachAccess);
        assertThat(coachPolicy.tierCode()).isEqualTo("COACH_PRO");
        assertThat(coachPolicy.periodType()).isEqualTo("MONTHLY");
        assertThat(coachPolicy.periodKey()).isEqualTo("2026-11");
        assertThat(coachPolicy.aiVerificationsLimit()).isEqualTo(400);
        assertThat(coachPolicy.hybridTwinsLimit()).isEqualTo(400);
        assertThat(coachPolicy.aiCostCapMicros()).isEqualTo(4_200_000L);
        assertThat(coachPolicy.resetsAt()).isEqualTo(Instant.parse("2026-12-01T00:00:00Z"));

        var seasonAccess = LearningOsTierCapabilityPolicy.project(
            EntitlementTier.PRO_PLUS, EntitlementStatus.ACTIVE, EntitlementSource.APP_STORE_SUBSCRIPTION,
            NOW.plusSeconds(86400), null, true, true, NOW
        );
        var seasonPolicy = policy.resolve(seasonAccess);
        assertThat(seasonPolicy.tierCode()).isEqualTo("YKS_SEASON_PASS");
        assertThat(seasonPolicy.periodType()).isEqualTo("MONTHLY");
        assertThat(seasonPolicy.periodKey()).isEqualTo("2026-11");
        assertThat(seasonPolicy.aiVerificationsLimit()).isEqualTo(400);
        assertThat(seasonPolicy.hybridTwinsLimit()).isEqualTo(400);
        assertThat(seasonPolicy.aiCostCapMicros()).isEqualTo(4_200_000L);
        assertThat(seasonPolicy.resetsAt()).isEqualTo(Instant.parse("2026-12-01T00:00:00Z"));
    }

    @Test
    void deterministicOperationsAreAlwaysExemptFromPaidQuota() {
        FairUsePolicy policy = new FairUsePolicy(Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(policy.requiresPaidQuota(QuotaOperationType.DETERMINISTIC_TWIN)).isFalse();
        assertThat(policy.requiresPaidQuota(QuotaOperationType.DETERMINISTIC_SYMBOLIC_VERIFY)).isFalse();
        assertThat(policy.requiresPaidQuota(QuotaOperationType.AI_VERIFICATION)).isTrue();
        assertThat(policy.requiresPaidQuota(QuotaOperationType.HYBRID_TWIN)).isTrue();
        assertThat(policy.requiresPaidQuota(QuotaOperationType.HANDWRITING_DIAGNOSTIC)).isTrue();
    }

    @Test
    void expiredOrRevokedAccessDowngradesQuotaPolicyToFreeAndHandlesYearRollover() {
        Instant decEnd = Instant.parse("2026-12-31T23:59:59Z");
        FairUsePolicy policy = new FairUsePolicy(Clock.fixed(decEnd, ZoneOffset.UTC));

        var expiredPro = LearningOsTierCapabilityPolicy.project(
            EntitlementTier.PRO, EntitlementStatus.EXPIRED, EntitlementSource.APP_STORE_SUBSCRIPTION,
            decEnd.plusSeconds(3600), null, false, true, decEnd
        );
        var resolvedExpired = policy.resolve(expiredPro);
        assertThat(resolvedExpired.tierCode()).isEqualTo("FREE");
        assertThat(resolvedExpired.periodKey()).isEqualTo("2026-12-31");
        assertThat(resolvedExpired.resetsAt()).isEqualTo(Instant.parse("2027-01-01T00:00:00Z"));

        var monthlyRollover = policy.resolveForTierCode("PRO", decEnd);
        assertThat(monthlyRollover.periodKey()).isEqualTo("2026-12");
        assertThat(monthlyRollover.resetsAt()).isEqualTo(Instant.parse("2027-01-01T00:00:00Z"));
    }
}
