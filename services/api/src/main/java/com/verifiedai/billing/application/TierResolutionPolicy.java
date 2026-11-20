package com.verifiedai.billing.application;

import com.verifiedai.billing.domain.model.EntitlementTier;

public final class TierResolutionPolicy {
    private TierResolutionPolicy() {}
    public static EntitlementTier storageTier(String presentation) {
        return "COACH_PRO".equals(presentation) ? EntitlementTier.PRO_PLUS : EntitlementTier.valueOf(presentation);
    }

    public static String presentation(EntitlementTier tier) {
        return tier == EntitlementTier.PRO_PLUS ? "COACH_PRO" : tier.name();
    }
}
