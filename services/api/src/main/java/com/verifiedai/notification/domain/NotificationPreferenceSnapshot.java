package com.verifiedai.notification.domain;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Immutable domain snapshot of a user's notification preferences.
 */
public record NotificationPreferenceSnapshot(
    UUID userId,
    boolean pushEnabled,
    boolean studyPlanEnabled,
    boolean spacedReviewEnabled,
    boolean examDeadlineEnabled,
    boolean billingTrialReminderEnabled,
    boolean accountSecurityEnabled,
    boolean quietHoursEnabled,
    LocalTime quietHoursStartLocal,
    LocalTime quietHoursEndLocal,
    String timezone,
    int dailyCap,
    long version,
    Instant createdAt,
    Instant updatedAt
) {
    public static final LocalTime DEFAULT_QUIET_START = LocalTime.of(22, 0);
    public static final LocalTime DEFAULT_QUIET_END = LocalTime.of(8, 0);
    public static final String DEFAULT_TIMEZONE = "UTC";
    public static final int DEFAULT_DAILY_CAP = 2;

    public static NotificationPreferenceSnapshot defaultFor(UUID userId, Instant now) {
        return defaultFor(userId, DEFAULT_DAILY_CAP, now);
    }

    public static NotificationPreferenceSnapshot defaultFor(UUID userId, int defaultDailyCap, Instant now) {
        return new NotificationPreferenceSnapshot(
            userId,
            false,
            false,
            false,
            false,
            false,
            true,
            true,
            DEFAULT_QUIET_START,
            DEFAULT_QUIET_END,
            DEFAULT_TIMEZONE,
            defaultDailyCap,
            0L,
            now,
            now
        );
    }

    public boolean isCategoryEnabled(NotificationCategory category) {
        return switch (category) {
            case STUDY_PLAN -> studyPlanEnabled;
            case SPACED_REVIEW -> spacedReviewEnabled;
            case EXAM_DEADLINE -> examDeadlineEnabled;
            case BILLING_TRIAL_REMINDER -> billingTrialReminderEnabled;
            case ACCOUNT_SECURITY -> accountSecurityEnabled;
        };
    }
}
