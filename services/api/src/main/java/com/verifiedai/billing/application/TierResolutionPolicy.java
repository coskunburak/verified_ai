package com.verifiedai.billing.application;

import com.verifiedai.billing.domain.model.EntitlementTier;

public final class TierResolutionPolicy {
    private TierResolutionPolicy() {}
    public static EntitlementTier storageTier(String presentation) {
        if (presentation == null) throw new IllegalArgumentException("Missing tier");
        return switch (presentation) {
            case "FREE" -> EntitlementTier.FREE;
            case "PRO" -> EntitlementTier.PRO;
            case "COACH_PRO", "PRO_PLUS" -> EntitlementTier.PRO_PLUS;
            default -> throw new IllegalArgumentException("Unsupported consumer tier");
        };
    }

    public static String presentation(EntitlementTier tier) {
        return tier == EntitlementTier.PRO_PLUS ? "COACH_PRO" : tier.name();
    }
}
