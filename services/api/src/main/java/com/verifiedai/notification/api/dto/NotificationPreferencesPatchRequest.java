package com.verifiedai.notification.api.dto;

import com.verifiedai.notification.application.UpdateNotificationPreferencesCommand;

public record NotificationPreferencesPatchRequest(
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
    public UpdateNotificationPreferencesCommand toCommand() {
        return new UpdateNotificationPreferencesCommand(
            pushEnabled,
            studyPlanEnabled,
            spacedReviewEnabled,
            examDeadlineEnabled,
            billingTrialReminderEnabled,
            accountSecurityEnabled,
            quietHoursEnabled,
            quietHoursStartLocal,
            quietHoursEndLocal,
            timezone,
            dailyCap,
            expectedVersion,
            trainingEligible
        );
    }
}
