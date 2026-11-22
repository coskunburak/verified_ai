package com.verifiedai.billing.application;

import com.verifiedai.billing.domain.model.EntitlementTier;

public enum LearningOsCapability {
    ATTEMPT_VERIFY(EntitlementTier.FREE), DETERMINISTIC_TWIN(EntitlementTier.FREE),
    HYBRID_TWIN(EntitlementTier.PRO), MISTAKE_PASSPORT_FULL(EntitlementTier.PRO),
    DAILY_COACH_MISSION(EntitlementTier.PRO_PLUS), MICRO_LESSON(EntitlementTier.PRO_PLUS),
    EXAM_READINESS_SIMULATOR(EntitlementTier.PRO_PLUS), TIMED_MOCK_SESSION(EntitlementTier.PRO_PLUS);

    // Add-on availability is separate from consumer subscription capabilities.
    public enum AddOn {
        TUTOR_COPILOT_PACK;
        public String availability() { return "NOT_AVAILABLE"; }
    }

    private final EntitlementTier minimum;
    LearningOsCapability(EntitlementTier minimum) { this.minimum = minimum; }
    boolean grantedBy(EntitlementTier tier) { return tier.includes(minimum); }
}
