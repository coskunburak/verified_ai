package com.verifiedai.studyplan.gamification;

/**
 * Non-stigmatizing learner streak continuity state for Sprint 8.8.
 */
public enum StreakStatus {
    ACTIVE_VERIFIED_TODAY(
            "gamification.streak.status.active_verified_today.headline",
            "gamification.streak.status.active_verified_today.body"
    ),
    CONTINUITY_PRESERVED_TODAY(
            "gamification.streak.status.continuity_preserved_today.headline",
            "gamification.streak.status.continuity_preserved_today.body"
    ),
    FREEZE_PROTECTED(
            "gamification.streak.status.freeze_protected.headline",
            "gamification.streak.status.freeze_protected.body"
    ),
    AT_RISK_TODAY(
            "gamification.streak.status.at_risk_today.headline",
            "gamification.streak.status.at_risk_today.body"
    ),
    FRESH_START_READY(
            "gamification.streak.status.fresh_start_ready.headline",
            "gamification.streak.status.fresh_start_ready.body"
    );

    private final String supportiveHeadlineKey;
    private final String supportiveBodyKey;

    StreakStatus(String supportiveHeadlineKey, String supportiveBodyKey) {
        this.supportiveHeadlineKey = supportiveHeadlineKey;
        this.supportiveBodyKey = supportiveBodyKey;
    }

    public String supportiveHeadlineKey() {
        return supportiveHeadlineKey;
    }

    public String supportiveBodyKey() {
        return supportiveBodyKey;
    }
}
