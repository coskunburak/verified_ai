package com.verifiedai.notification.domain;

/**
 * Closed set of lifecycle messaging trigger evaluation outcomes persisted in {@code lifecycle_messaging_evaluations}.
 */
public enum LifecycleEvaluationDecision {
    SCHEDULED(false),
    DEFERRED_QUIET_HOURS(false),
    SUPPRESSED(false),
    REPLAYED(false),
    SKIPPED_ALREADY_COMPLETED(true),
    SKIPPED_COOLDOWN_ACTIVE(true),
    SKIPPED_OPTED_OUT(true),
    SKIPPED_NOT_ELIGIBLE(true),
    SKIPPED_FEATURE_DISABLED(true),
    CANCELLED_OBSOLETE(false);

    private final boolean skipDecision;

    LifecycleEvaluationDecision(boolean skipDecision) {
        this.skipDecision = skipDecision;
    }

    public boolean isSkipDecision() {
        return skipDecision;
    }
}
