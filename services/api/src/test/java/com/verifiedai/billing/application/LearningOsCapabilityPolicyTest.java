package com.verifiedai.billing.application;

import com.verifiedai.billing.domain.model.EntitlementSource;
import com.verifiedai.billing.domain.model.EntitlementStatus;
import com.verifiedai.billing.domain.model.EntitlementTier;
import com.verifiedai.billing.domain.model.PremiumCapability;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LearningOsCapabilityPolicyTest {
    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");

    @Test
    void verifiesAllEightCapabilitiesForEveryPackage() {
        for (var tier : EntitlementTier.values()) {
            for (boolean season : new boolean[]{false, true}) {
                if (season && tier != EntitlementTier.PRO_PLUS) {
                    continue;
                }
                var result = LearningOsTierCapabilityPolicy.project(
                    tier,
                    EntitlementStatus.ACTIVE,
                    EntitlementSource.APP_STORE_SUBSCRIPTION,
                    NOW.plusSeconds(60),
                    null,
                    season,
                    true,
                    NOW
                );
                var expectedLearningOs = switch (tier) {
                    case FREE -> List.of(
                        LearningOsCapability.ATTEMPT_VERIFY,
                        LearningOsCapability.DETERMINISTIC_TWIN
                    );
                    case PRO -> List.of(
                        LearningOsCapability.ATTEMPT_VERIFY,
                        LearningOsCapability.DETERMINISTIC_TWIN,
                        LearningOsCapability.HYBRID_TWIN,
                        LearningOsCapability.MISTAKE_PASSPORT_FULL
                    );
                    case PRO_PLUS -> List.of(LearningOsCapability.values());
                };
                assertThat(result.learningOsCapabilities()).containsExactlyElementsOf(expectedLearningOs);
                assertThat(result.capabilities()).contains(PremiumCapability.BASIC_SOLVE);
                assertThat(result.historyAccess()).containsEntry("priorExams", "SUMMARY");
                if (season) {
                    assertThat(result.effectiveTier()).isEqualTo("COACH_PRO");
                    assertThat(result.legacyTier()).isEqualTo(EntitlementTier.PRO_PLUS);
                    assertThat(result.packageCode()).isEqualTo("YKS_SEASON_PASS");
                }
            }
        }
        assertThat(LearningOsCapability.values()).hasSize(8);
        assertThat(PremiumCapability.values()).hasSize(7);
        assertThat(LearningOsCapability.AddOn.TUTOR_COPILOT_PACK.availability()).isEqualTo("NOT_AVAILABLE");
        assertThatThrownBy(() -> TierResolutionPolicy.storageTier("YKS_SEASON_PASS"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TierResolutionPolicy.storageTier("TUTOR_COPILOT_PACK"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preservesFreeBaselineAndReadOnlyHistoryWhenExpiredOrRevoked() {
        for (var terminalStatus : List.of(EntitlementStatus.EXPIRED, EntitlementStatus.REVOKED)) {
            var result = LearningOsTierCapabilityPolicy.project(
                EntitlementTier.PRO_PLUS,
                terminalStatus,
                EntitlementSource.APP_STORE_SUBSCRIPTION,
                NOW.plusSeconds(3600),
                NOW.plusSeconds(7200),
                false,
                true,
                NOW
            );
            assertThat(result.effectiveTier()).isEqualTo("FREE");
            assertThat(result.legacyTier()).isEqualTo(EntitlementTier.PRO_PLUS);
            assertThat(result.packageCode()).isEqualTo("FREE");
            assertThat(result.status()).isEqualTo(terminalStatus);
            assertThat(result.paidAccessActive()).isFalse();
            assertThat(result.capabilities()).containsExactly(PremiumCapability.BASIC_SOLVE);
            assertThat(result.learningOsCapabilities()).containsExactly(
                LearningOsCapability.ATTEMPT_VERIFY,
                LearningOsCapability.DETERMINISTIC_TWIN
            );
            assertThat(result.historyAccess()).containsExactlyInAnyOrderEntriesOf(
                java.util.Map.of(
                    "mistakePassport", "SUMMARY",
                    "skillMastery", "SUMMARY",
                    "priorExams", "SUMMARY"
                )
            );
        }
    }

    @Test
    void enforcesExactClockBoundariesAndExpiredActiveProjection() {
        for (var status : EntitlementStatus.values()) {
            assertThat(LearningOsTierCapabilityPolicy.paid(status, NOW, NOW, false, NOW)).isFalse();
            assertThat(LearningOsTierCapabilityPolicy.paid(status, null, null, false, NOW)).isFalse();
            assertThat(LearningOsTierCapabilityPolicy.paid(status, NOW, NOW.plusMillis(1), true, NOW)).isFalse();
            assertThat(LearningOsTierCapabilityPolicy.paid(status, NOW, NOW.plusMillis(1), false, NOW))
                .isEqualTo(status == EntitlementStatus.GRACE_PERIOD || status == EntitlementStatus.BILLING_RETRY);
            assertThat(LearningOsTierCapabilityPolicy.paid(status, NOW.plusMillis(1), null, false, NOW))
                .isEqualTo(status == EntitlementStatus.ACTIVE || status == EntitlementStatus.BILLING_RETRY);
        }

        var expiredActiveOnRead = LearningOsTierCapabilityPolicy.project(
            EntitlementTier.PRO,
            EntitlementStatus.ACTIVE,
            EntitlementSource.APP_STORE_SUBSCRIPTION,
            NOW,
            null,
            false,
            true,
            NOW
        );
        assertThat(expiredActiveOnRead.status()).isEqualTo(EntitlementStatus.EXPIRED);
        assertThat(expiredActiveOnRead.effectiveTier()).isEqualTo("FREE");
        assertThat(expiredActiveOnRead.paidAccessActive()).isFalse();
        assertThat(expiredActiveOnRead.capabilities()).containsExactly(PremiumCapability.BASIC_SOLVE);
    }

    @Test
    void featureDisabledSuppressesPaidLearningOsCapabilitiesWithoutDroppingLegacyTier() {
        var result = LearningOsTierCapabilityPolicy.project(
            EntitlementTier.PRO_PLUS,
            EntitlementStatus.ACTIVE,
            EntitlementSource.APP_STORE_SUBSCRIPTION,
            NOW.plusSeconds(3600),
            null,
            false,
            false,
            NOW
        );
        assertThat(result.featureEnabled()).isFalse();
        assertThat(result.effectiveTier()).isEqualTo("COACH_PRO");
        assertThat(result.paidAccessActive()).isTrue();
        assertThat(result.learningOsCapabilities()).containsExactly(
            LearningOsCapability.ATTEMPT_VERIFY,
            LearningOsCapability.DETERMINISTIC_TWIN
        );
    }
}
