package com.verifiedai.billing.application;

public record CapabilityDecision(
    boolean allowed,
    LearningOsCapability capability,
    String effectiveTier,
    String packageCode,
    String denyReason,
    boolean featureEnabled
) {}
