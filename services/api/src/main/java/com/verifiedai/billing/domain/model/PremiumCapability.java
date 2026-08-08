package com.verifiedai.billing.domain.model;

public enum PremiumCapability {
    BASIC_SOLVE(EntitlementTier.FREE),
    VERIFIED_SOLVE(EntitlementTier.PRO),
    ADVANCED_TUTOR(EntitlementTier.PRO),
    MISTAKE_HISTORY(EntitlementTier.PRO),
    ADAPTIVE_PLAN(EntitlementTier.PRO),
    MOCK_EXAM(EntitlementTier.PRO_PLUS),
    PREMIUM_MODEL_FALLBACK(EntitlementTier.PRO_PLUS);

    private final EntitlementTier minimumTier;

    PremiumCapability(EntitlementTier minimumTier) {
        this.minimumTier = minimumTier;
    }

    public EntitlementTier minimumTier() {
        return minimumTier;
    }
}
