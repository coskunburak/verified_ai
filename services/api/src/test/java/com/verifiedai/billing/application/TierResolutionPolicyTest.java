package com.verifiedai.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.billing.domain.model.EntitlementTier;
import org.junit.jupiter.api.Test;

final class TierResolutionPolicyTest {

    @Test
    void mapsConsumerTiersBetweenStorageAndPresentationDeterministically() {
        assertThat(TierResolutionPolicy.storageTier("FREE")).isEqualTo(EntitlementTier.FREE);
        assertThat(TierResolutionPolicy.storageTier("PRO")).isEqualTo(EntitlementTier.PRO);
        assertThat(TierResolutionPolicy.storageTier("PRO_PLUS")).isEqualTo(EntitlementTier.PRO_PLUS);
        assertThat(TierResolutionPolicy.storageTier("COACH_PRO")).isEqualTo(EntitlementTier.PRO_PLUS);

        assertThat(TierResolutionPolicy.presentation(EntitlementTier.FREE)).isEqualTo("FREE");
        assertThat(TierResolutionPolicy.presentation(EntitlementTier.PRO)).isEqualTo("PRO");
        assertThat(TierResolutionPolicy.presentation(EntitlementTier.PRO_PLUS)).isEqualTo("COACH_PRO");
    }

    @Test
    void rejectsUnknownSeasonAddonOrMalformedTierValuesFailClosed() {
        for (String invalid : new String[]{
            null, "", " ", "free", "pro", "YKS_SEASON_PASS", "TUTOR_COPILOT_PACK", "ADMIN", "CLIENT_PRO"
        }) {
            assertThatThrownBy(() -> TierResolutionPolicy.storageTier(invalid))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
