package com.verifiedai.billing.domain.model;

public enum EntitlementTier {
    FREE,
    PRO,
    PRO_PLUS;

    public boolean includes(EntitlementTier requiredTier) {
        return ordinal() >= requiredTier.ordinal();
    }
}
