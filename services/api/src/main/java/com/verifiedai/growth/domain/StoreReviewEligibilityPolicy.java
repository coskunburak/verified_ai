package com.verifiedai.growth.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Pure deterministic eligibility policy for non-spammy store review prompts (INV-GRW-004 & INV-MON-001).
 */
public final class StoreReviewEligibilityPolicy {

    private StoreReviewEligibilityPolicy() {
    }

    public record Inputs(
        StoreReviewTriggerMoment triggerMoment,
        boolean activeTimerRunning,
        boolean hasActiveExamSession,
        boolean userDeclinedPermanently,
        boolean hasRecentFriction,
        long accountAgeDays,
        long verifiedLearningActions,
        int promptsShownLast365Days,
        Instant lastShownOrDismissedAt,
        int minAccountAgeDays,
        int minVerifiedActions,
        int maxPromptsPerYear,
        int cooldownDays,
        Instant now
    ) {
        public Inputs {
            Objects.requireNonNull(triggerMoment, "triggerMoment must not be null");
            Objects.requireNonNull(now, "now must not be null");
        }
    }

    public record Decision(
        boolean eligible,
        StoreReviewIneligibilityReason reason,
        Instant nextEligibleAt
    ) {
    }

    public static Decision evaluate(Inputs inputs) {
        Objects.requireNonNull(inputs, "inputs must not be null");

        if (inputs.activeTimerRunning() || inputs.hasActiveExamSession()) {
            return new Decision(false, StoreReviewIneligibilityReason.INELIGIBLE_ACTIVE_TIMER, null);
        }
        if (inputs.userDeclinedPermanently()) {
            return new Decision(false, StoreReviewIneligibilityReason.INELIGIBLE_USER_DECLINED, null);
        }
        if (inputs.hasRecentFriction()) {
            return new Decision(false, StoreReviewIneligibilityReason.INELIGIBLE_RECENT_FRICTION, null);
        }
        if (inputs.accountAgeDays() < inputs.minAccountAgeDays()
            || inputs.verifiedLearningActions() < inputs.minVerifiedActions()) {
            return new Decision(false, StoreReviewIneligibilityReason.INELIGIBLE_INSUFFICIENT_ENGAGEMENT, null);
        }
        if (inputs.promptsShownLast365Days() >= inputs.maxPromptsPerYear()) {
            return new Decision(false, StoreReviewIneligibilityReason.INELIGIBLE_ANNUAL_CAP_REACHED, null);
        }
        if (inputs.lastShownOrDismissedAt() != null) {
            Instant cooldownEndsAt = inputs.lastShownOrDismissedAt().plus(Duration.ofDays(inputs.cooldownDays()));
            if (cooldownEndsAt.isAfter(inputs.now())) {
                return new Decision(false, StoreReviewIneligibilityReason.INELIGIBLE_COOLDOWN_ACTIVE, cooldownEndsAt);
            }
        }
        return new Decision(true, StoreReviewIneligibilityReason.ELIGIBLE, null);
    }
}
