package com.verifiedai.notification.application;

/**
 * Application command for updating notification preferences with optimistic locking.
 */
public record UpdateNotificationPreferencesCommand(
    Boolean pushEnabled,
    Boolean studyPlanEnabled,
    Boolean spacedReviewEnabled,
    Boolean examDeadlineEnabled,
    Boolean billingTrialReminderEnabled,
    Boolean accountSecurityEnabled,
    Boolean quietHoursEnabled,
    String quietHoursStartLocal,
    String quietHoursEndLocal,
    String timezone,
    Integer dailyCap,
    Long expectedVersion,
    Boolean trainingEligible
) {
}
