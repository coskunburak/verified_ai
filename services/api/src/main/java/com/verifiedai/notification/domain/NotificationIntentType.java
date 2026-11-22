package com.verifiedai.notification.domain;

/**
 * Closed enum of typed notification intents mapped deterministically to {@link NotificationCategory}.
 */
public enum NotificationIntentType {
    STUDY_PLAN_DAILY_MISSION(NotificationCategory.STUDY_PLAN),
    SPACED_REVIEW_DUE(NotificationCategory.SPACED_REVIEW),
    EXAM_DEADLINE_COUNTDOWN(NotificationCategory.EXAM_DEADLINE),
    TRIAL_ENDING_48H_REMINDER(NotificationCategory.BILLING_TRIAL_REMINDER),
    ACCOUNT_SECURITY_ALERT(NotificationCategory.ACCOUNT_SECURITY);

    private final NotificationCategory category;

    NotificationIntentType(NotificationCategory category) {
        this.category = category;
    }

    public NotificationCategory category() {
        return category;
    }

    public boolean matchesCategory(NotificationCategory candidate) {
        return category == candidate;
    }
}
