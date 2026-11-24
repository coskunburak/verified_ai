package com.verifiedai.growth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

final class StoreReviewEligibilityPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");

    @Test
    void enforcesPriorityOrderSuppressionAndEligibility() {
        // 1. Active timer suppresses everything else
        var activeTimerDecision = StoreReviewEligibilityPolicy.evaluate(
            new StoreReviewEligibilityPolicy.Inputs(
                StoreReviewTriggerMoment.INDEPENDENT_TWIN_MASTERED,
                true,
                false,
                false,
                false,
                10,
                10,
                0,
                null,
                3,
                3,
                3,
                60,
                NOW
            )
        );
        assertThat(activeTimerDecision.eligible()).isFalse();
        assertThat(activeTimerDecision.reason()).isEqualTo(StoreReviewIneligibilityReason.INELIGIBLE_ACTIVE_TIMER);

        // Active exam session also suppresses
        var activeExamDecision = StoreReviewEligibilityPolicy.evaluate(
            new StoreReviewEligibilityPolicy.Inputs(
                StoreReviewTriggerMoment.MOCK_EXAM_COMPLETED,
                false,
                true,
                true,
                true,
                10,
                10,
                3,
                NOW.minus(1, ChronoUnit.DAYS),
                3,
                3,
                3,
                60,
                NOW
            )
        );
        assertThat(activeExamDecision.eligible()).isFalse();
        assertThat(activeExamDecision.reason()).isEqualTo(StoreReviewIneligibilityReason.INELIGIBLE_ACTIVE_TIMER);

        // 2. Permanent user opt-out
        var declinedDecision = StoreReviewEligibilityPolicy.evaluate(
            new StoreReviewEligibilityPolicy.Inputs(
                StoreReviewTriggerMoment.MASTERY_MILESTONE_UNLOCKED,
                false,
                false,
                true,
                true,
                10,
                10,
                0,
                null,
                3,
                3,
                3,
                60,
                NOW
            )
        );
        assertThat(declinedDecision.eligible()).isFalse();
        assertThat(declinedDecision.reason()).isEqualTo(StoreReviewIneligibilityReason.INELIGIBLE_USER_DECLINED);

        // 3. Recent wrong-answer friction
        var frictionDecision = StoreReviewEligibilityPolicy.evaluate(
            new StoreReviewEligibilityPolicy.Inputs(
                StoreReviewTriggerMoment.WEEKLY_PROGRESS_WIN,
                false,
                false,
                false,
                true,
                10,
                10,
                0,
                null,
                3,
                3,
                3,
                60,
                NOW
            )
        );
        assertThat(frictionDecision.eligible()).isFalse();
        assertThat(frictionDecision.reason()).isEqualTo(StoreReviewIneligibilityReason.INELIGIBLE_RECENT_FRICTION);

        // 4. Insufficient engagement (account age < 3 or actions < 3)
        var engagementDecision = StoreReviewEligibilityPolicy.evaluate(
            new StoreReviewEligibilityPolicy.Inputs(
                StoreReviewTriggerMoment.INDEPENDENT_TWIN_MASTERED,
                false,
                false,
                false,
                false,
                2,
                5,
                0,
                null,
                3,
                3,
                3,
                60,
                NOW
            )
        );
        assertThat(engagementDecision.eligible()).isFalse();
        assertThat(engagementDecision.reason()).isEqualTo(StoreReviewIneligibilityReason.INELIGIBLE_INSUFFICIENT_ENGAGEMENT);

        // 5. Rolling 365-day annual cap reached (>= 3)
        var annualCapDecision = StoreReviewEligibilityPolicy.evaluate(
            new StoreReviewEligibilityPolicy.Inputs(
                StoreReviewTriggerMoment.INDEPENDENT_TWIN_MASTERED,
                false,
                false,
                false,
                false,
                200,
                20,
                3,
                NOW.minus(90, ChronoUnit.DAYS),
                3,
                3,
                3,
                60,
                NOW
            )
        );
        assertThat(annualCapDecision.eligible()).isFalse();
        assertThat(annualCapDecision.reason()).isEqualTo(StoreReviewIneligibilityReason.INELIGIBLE_ANNUAL_CAP_REACHED);

        // 6. 60-day cooldown active -> deterministic nextEligibleAt
        Instant shownAt = NOW.minus(10, ChronoUnit.DAYS);
        var cooldownDecision = StoreReviewEligibilityPolicy.evaluate(
            new StoreReviewEligibilityPolicy.Inputs(
                StoreReviewTriggerMoment.INDEPENDENT_TWIN_MASTERED,
                false,
                false,
                false,
                false,
                30,
                10,
                1,
                shownAt,
                3,
                3,
                3,
                60,
                NOW
            )
        );
        assertThat(cooldownDecision.eligible()).isFalse();
        assertThat(cooldownDecision.reason()).isEqualTo(StoreReviewIneligibilityReason.INELIGIBLE_COOLDOWN_ACTIVE);
        assertThat(cooldownDecision.nextEligibleAt()).isEqualTo(shownAt.plus(60, ChronoUnit.DAYS));

        // 7. Eligible once all gates pass
        var eligibleDecision = StoreReviewEligibilityPolicy.evaluate(
            new StoreReviewEligibilityPolicy.Inputs(
                StoreReviewTriggerMoment.INDEPENDENT_TWIN_MASTERED,
                false,
                false,
                false,
                false,
                30,
                10,
                1,
                NOW.minus(61, ChronoUnit.DAYS),
                3,
                3,
                3,
                60,
                NOW
            )
        );
        assertThat(eligibleDecision.eligible()).isTrue();
        assertThat(eligibleDecision.reason()).isEqualTo(StoreReviewIneligibilityReason.ELIGIBLE);
        assertThat(eligibleDecision.nextEligibleAt()).isNull();
    }
}
