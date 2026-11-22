package com.verifiedai.billing.application;

/** Operations governed by Sprint 10.3 usage quotas and deterministic exemption rules. */
public enum QuotaOperationType {
    AI_VERIFICATION,
    HYBRID_TWIN,
    HANDWRITING_DIAGNOSTIC,
    DETERMINISTIC_TWIN,
    DETERMINISTIC_SYMBOLIC_VERIFY
}
