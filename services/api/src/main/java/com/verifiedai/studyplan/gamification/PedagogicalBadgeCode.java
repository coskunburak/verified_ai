package com.verifiedai.studyplan.gamification;

/**
 * Deterministic pedagogical milestones awarded exclusively from verified evidence.
 */
public enum PedagogicalBadgeCode {
    FIRST_INDEPENDENT_TWIN(
            PedagogicalBadgeCategory.INDEPENDENT_TRANSFER,
            "gamification.badge.first_independent_twin.title",
            "gamification.badge.first_independent_twin.description"
    ),
    THREE_DAY_INDEPENDENT_STREAK(
            PedagogicalBadgeCategory.HABIT_CONSISTENCY,
            "gamification.badge.three_day_independent_streak.title",
            "gamification.badge.three_day_independent_streak.description"
    ),
    SEVEN_DAY_INDEPENDENT_STREAK(
            PedagogicalBadgeCategory.HABIT_CONSISTENCY,
            "gamification.badge.seven_day_independent_streak.title",
            "gamification.badge.seven_day_independent_streak.description"
    ),
    MISCONCEPTION_OVERCOME(
            PedagogicalBadgeCategory.MISCONCEPTION_REPAIR,
            "gamification.badge.misconception_overcome.title",
            "gamification.badge.misconception_overcome.description"
    ),
    RETENTION_GUARDIAN(
            PedagogicalBadgeCategory.SPACED_RETENTION,
            "gamification.badge.retention_guardian.title",
            "gamification.badge.retention_guardian.description"
    ),
    CONCEPT_ARCHITECT(
            PedagogicalBadgeCategory.CONCEPT_MASTERY,
            "gamification.badge.concept_architect.title",
            "gamification.badge.concept_architect.description"
    );

    private final PedagogicalBadgeCategory badgeCategory;
    private final String titleKey;
    private final String descriptionKey;

    PedagogicalBadgeCode(
            PedagogicalBadgeCategory badgeCategory,
            String titleKey,
            String descriptionKey
    ) {
        this.badgeCategory = badgeCategory;
        this.titleKey = titleKey;
        this.descriptionKey = descriptionKey;
    }

    public PedagogicalBadgeCategory badgeCategory() {
        return badgeCategory;
    }

    public String titleKey() {
        return titleKey;
    }

    public String descriptionKey() {
        return descriptionKey;
    }
}
