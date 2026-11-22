package com.verifiedai.billing.application;

import com.verifiedai.billing.domain.model.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;

final class LearningOsTierCapabilityPolicy {
    static boolean paid(EntitlementStatus status, Instant end, Instant grace, boolean season, Instant now) {
        boolean within = end != null && end.isAfter(now);
        boolean extended = grace != null && grace.isAfter(now);
        if (season && !within) return false;
        return switch (status) {
            case ACTIVE -> within;
            case GRACE_PERIOD -> extended;
            case BILLING_RETRY -> within || extended;
            case EXPIRED, REVOKED -> false;
        };
    }

    static EffectiveEntitlementAccess project(EntitlementTier tier, EntitlementStatus status, EntitlementSource source,
        Instant end, Instant grace, boolean season, boolean enabled, Instant now) {
        boolean paid = tier != EntitlementTier.FREE && paid(status, end, grace, season, now);
        EntitlementStatus effectiveStatus = (tier != EntitlementTier.FREE && status == EntitlementStatus.ACTIVE && !paid)
            ? EntitlementStatus.EXPIRED
            : status;
        var effective = paid ? tier : EntitlementTier.FREE;
        String display = TierResolutionPolicy.presentation(effective);
        var learningTier = enabled ? effective : EntitlementTier.FREE;
        return new EffectiveEntitlementAccess(display, tier, effectiveStatus, source, end,
            Arrays.stream(PremiumCapability.values()).filter(c -> effective.includes(c.minimumTier())).toList(),
            season && paid ? "YKS_SEASON_PASS" : display, grace, enabled,
            Arrays.stream(LearningOsCapability.values()).filter(c -> c.grantedBy(learningTier)).toList(),
            Map.of("mistakePassport", paid ? "FULL" : "SUMMARY", "skillMastery", paid ? "FULL" : "SUMMARY",
                   "priorExams", "SUMMARY"), now, paid);
    }
}
