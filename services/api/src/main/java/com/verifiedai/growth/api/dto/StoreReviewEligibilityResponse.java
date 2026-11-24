package com.verifiedai.growth.api.dto;

import com.verifiedai.growth.application.StoreReviewEligibilityView;
import java.time.Instant;

public record StoreReviewEligibilityResponse(
    boolean eligible,
    String reasonCode,
    Instant nextEligibleAt,
    String triggerMoment,
    int promptsShownLast365Days,
    boolean trainingEligible,
    int llmTokensUsed
) {
    public static StoreReviewEligibilityResponse from(StoreReviewEligibilityView view) {
        return new StoreReviewEligibilityResponse(
            view.eligible(),
            view.reasonCode(),
            view.nextEligibleAt(),
            view.triggerMoment(),
            view.promptsShownLast365Days(),
            view.trainingEligible(),
            view.llmTokensUsed()
        );
    }
}
