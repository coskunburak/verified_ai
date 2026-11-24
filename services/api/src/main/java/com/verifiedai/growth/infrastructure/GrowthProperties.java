package com.verifiedai.growth.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Fail-closed configuration properties for Sprint 10.9 Organic Growth Loops.
 */
@ConfigurationProperties(prefix = "verifiedai.growth")
public record GrowthProperties(
    boolean sharingEnabled,
    boolean referralEnabled,
    boolean storeReviewEnabled,
    int shareDefaultTtlDays,
    int shareMaxTtlDays,
    int maxActiveSharesPerUser,
    int maxReferralsPerMonth,
    int maxRewardsPerYear,
    int maxAttributionsPerDevice,
    int referralRewardSolveCredits,
    int reviewCooldownDays,
    int reviewMaxPromptsPerYear,
    int reviewMinAccountAgeDays,
    int reviewMinVerifiedActions,
    int reviewFrictionLookbackDays
) {

    public GrowthProperties {
        if (shareDefaultTtlDays < 1 || shareDefaultTtlDays > 90) {
            throw new IllegalArgumentException("verifiedai.growth.share-default-ttl-days must be between 1 and 90");
        }
        if (shareMaxTtlDays < 1 || shareMaxTtlDays > 90) {
            throw new IllegalArgumentException("verifiedai.growth.share-max-ttl-days must be between 1 and 90");
        }
        if (shareDefaultTtlDays > shareMaxTtlDays) {
            throw new IllegalArgumentException(
                "verifiedai.growth.share-default-ttl-days must not exceed share-max-ttl-days"
            );
        }
        if (maxActiveSharesPerUser < 1 || maxActiveSharesPerUser > 100) {
            throw new IllegalArgumentException(
                "verifiedai.growth.max-active-shares-per-user must be between 1 and 100"
            );
        }
        if (maxReferralsPerMonth < 1 || maxReferralsPerMonth > 50) {
            throw new IllegalArgumentException(
                "verifiedai.growth.max-referrals-per-month must be between 1 and 50"
            );
        }
        if (maxRewardsPerYear < 1 || maxRewardsPerYear > 50) {
            throw new IllegalArgumentException(
                "verifiedai.growth.max-rewards-per-year must be between 1 and 50"
            );
        }
        if (maxAttributionsPerDevice < 1 || maxAttributionsPerDevice > 5) {
            throw new IllegalArgumentException(
                "verifiedai.growth.max-attributions-per-device must be between 1 and 5"
            );
        }
        if (referralRewardSolveCredits < 1 || referralRewardSolveCredits > 20) {
            throw new IllegalArgumentException(
                "verifiedai.growth.referral-reward-solve-credits must be between 1 and 20"
            );
        }
        if (reviewCooldownDays < 30 || reviewCooldownDays > 365) {
            throw new IllegalArgumentException(
                "verifiedai.growth.review-cooldown-days must be between 30 and 365"
            );
        }
        if (reviewMaxPromptsPerYear < 1 || reviewMaxPromptsPerYear > 3) {
            throw new IllegalArgumentException(
                "verifiedai.growth.review-max-prompts-per-year must be between 1 and 3"
            );
        }
        if (reviewMinAccountAgeDays < 0 || reviewMinAccountAgeDays > 365) {
            throw new IllegalArgumentException(
                "verifiedai.growth.review-min-account-age-days must be between 0 and 365"
            );
        }
        if (reviewMinVerifiedActions < 1 || reviewMinVerifiedActions > 100) {
            throw new IllegalArgumentException(
                "verifiedai.growth.review-min-verified-actions must be between 1 and 100"
            );
        }
        if (reviewFrictionLookbackDays < 1 || reviewFrictionLookbackDays > 90) {
            throw new IllegalArgumentException(
                "verifiedai.growth.review-friction-lookback-days must be between 1 and 90"
            );
        }
    }

    public static GrowthProperties defaults() {
        return new GrowthProperties(
            false,
            false,
            false,
            30,
            90,
            25,
            10,
            12,
            2,
            5,
            60,
            3,
            3,
            3,
            7
        );
    }
}
