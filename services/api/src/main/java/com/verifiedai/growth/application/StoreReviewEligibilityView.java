package com.verifiedai.growth.application;

import java.time.Instant;

public record StoreReviewEligibilityView(
    boolean eligible,
    String reasonCode,
    Instant nextEligibleAt,
    String triggerMoment,
    int promptsShownLast365Days,
    boolean trainingEligible,
    int llmTokensUsed
) {
}
