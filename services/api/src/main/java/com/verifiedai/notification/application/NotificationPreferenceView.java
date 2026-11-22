package com.verifiedai.notification.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Public read projection of a user's notification preferences.
 */
public record NotificationPreferenceView(
    String schemaVersion,
    UUID userId,
    boolean pushEnabled,
    boolean studyPlanEnabled,
    boolean spacedReviewEnabled,
    boolean examDeadlineEnabled,
    boolean billingTrialReminderEnabled,
    boolean accountSecurityEnabled,
    boolean quietHoursEnabled,
    String quietHoursStartLocal,
    String quietHoursEndLocal,
    String timezone,
    int dailyCap,
    long version,
    boolean trainingEligible,
    Instant updatedAt
) {
}
