package com.verifiedai.notification.api.dto;

import com.verifiedai.notification.application.NotificationPreferenceView;
import java.time.Instant;
import java.util.UUID;

public record NotificationPreferencesResponse(
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
    public static NotificationPreferencesResponse from(NotificationPreferenceView view) {
        return new NotificationPreferencesResponse(
            view.schemaVersion(),
            view.userId(),
            view.pushEnabled(),
            view.studyPlanEnabled(),
            view.spacedReviewEnabled(),
            view.examDeadlineEnabled(),
            view.billingTrialReminderEnabled(),
            view.accountSecurityEnabled(),
            view.quietHoursEnabled(),
            view.quietHoursStartLocal(),
            view.quietHoursEndLocal(),
            view.timezone(),
            view.dailyCap(),
            view.version(),
            view.trainingEligible(),
            view.updatedAt()
        );
    }
}
