package com.verifiedai.billing.domain.model;

public enum EntitlementStatus {
    ACTIVE,
    GRACE_PERIOD,
    BILLING_RETRY,
    EXPIRED,
    REVOKED;

    public boolean grantsAccess() {
        return this == ACTIVE || this == GRACE_PERIOD || this == BILLING_RETRY;
    }
}
