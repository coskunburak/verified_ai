package com.verifiedai.growth.api.dto;

import com.verifiedai.growth.application.ReferralSummaryView;
import java.time.Instant;

public record ReferralSummaryResponse(
    String referralCode,
    String deepLinkUrl,
    boolean active,
    int totalAttributions,
    int qualifiedCount,
    int rewardedCount,
    int totalRewardCredits,
    int monthlyAttributionsRemaining,
    int annualRewardsRemaining,
    boolean trainingEligible,
    int llmTokensUsed,
    Instant createdAt
) {
    public static ReferralSummaryResponse from(ReferralSummaryView view) {
        return new ReferralSummaryResponse(
            view.referralCode(),
            view.deepLinkUrl(),
            view.active(),
            view.totalAttributions(),
            view.qualifiedCount(),
            view.rewardedCount(),
            view.totalRewardCredits(),
            view.monthlyAttributionsRemaining(),
            view.annualRewardsRemaining(),
            view.trainingEligible(),
            view.llmTokensUsed(),
            view.createdAt()
        );
    }
}
