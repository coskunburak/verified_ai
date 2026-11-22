package com.verifiedai.billing.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.verifiedai.billing.domain.model.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record EffectiveEntitlementAccess(String effectiveTier, EntitlementTier legacyTier, EntitlementStatus status,
    EntitlementSource source, Instant expiresAt, List<PremiumCapability> capabilities, String packageCode,
    Instant gracePeriodExpiresAt, boolean featureEnabled, List<LearningOsCapability> learningOsCapabilities,
    Map<String,String> historyAccess, Instant accessEvaluatedAt, boolean paidAccessActive,
    @JsonInclude(JsonInclude.Include.NON_NULL) QuotaSummary quotaSummary) {

    public EffectiveEntitlementAccess(String effectiveTier, EntitlementTier legacyTier, EntitlementStatus status,
        EntitlementSource source, Instant expiresAt, List<PremiumCapability> capabilities, String packageCode,
        Instant gracePeriodExpiresAt, boolean featureEnabled, List<LearningOsCapability> learningOsCapabilities,
        Map<String,String> historyAccess, Instant accessEvaluatedAt, boolean paidAccessActive) {
        this(effectiveTier, legacyTier, status, source, expiresAt, capabilities, packageCode, gracePeriodExpiresAt,
            featureEnabled, learningOsCapabilities, historyAccess, accessEvaluatedAt, paidAccessActive, null);
    }

    public EffectiveEntitlementAccess withQuotaSummary(QuotaSummary summary) {
        return new EffectiveEntitlementAccess(effectiveTier, legacyTier, status, source, expiresAt, capabilities,
            packageCode, gracePeriodExpiresAt, featureEnabled, learningOsCapabilities, historyAccess,
            accessEvaluatedAt, paidAccessActive, summary);
    }
}
