package com.verifiedai.growth.application;

import java.time.Instant;

public record ReferralSummaryView(
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
}
