package com.verifiedai.notification.application;

import com.verifiedai.notification.domain.NotificationPreferenceSnapshot;
import com.verifiedai.notification.domain.QuietHoursPolicy;
import com.verifiedai.notification.infrastructure.NotificationPreferenceJdbcRepository;
import com.verifiedai.notification.infrastructure.NotificationProperties;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service managing opt-in notification preferences with optimistic concurrency.
 */
@Service
public class NotificationPreferenceService {

    public static final String SCHEMA_VERSION = "notification-preferences-v1";

    private final NotificationPreferenceJdbcRepository preferenceRepository;
    private final NotificationProperties properties;
    private final NotificationMetrics metrics;
    private final Clock clock;

    public NotificationPreferenceService(
        NotificationPreferenceJdbcRepository preferenceRepository,
        NotificationProperties properties,
        NotificationMetrics metrics,
        Clock clock
    ) {
        this.preferenceRepository = preferenceRepository;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public NotificationPreferenceView getPreferencesForApi(UUID userId) {
        requireActiveAccount(userId);
        requireFeatureEnabled();
        return getPreferences(userId);
    }

    @Transactional(readOnly = true)
    public NotificationPreferenceView getPreferences(UUID userId) {
        Instant now = clock.instant();
        NotificationPreferenceSnapshot snapshot = preferenceRepository.findByUserId(userId)
            .orElseGet(() -> NotificationPreferenceSnapshot.defaultFor(userId, properties.defaultDailyCap(), now));
        return toView(snapshot);
    }

    @Transactional
    public NotificationPreferenceView updatePreferences(UUID userId, UpdateNotificationPreferencesCommand command) {
        requireActiveAccount(userId);
        requireFeatureEnabled();

        try {
            if (command == null || command.expectedVersion() == null || command.expectedVersion() < 0) {
                throw validationProblem("Notification preference update requires a non-negative expectedVersion");
            }
            if (Boolean.TRUE.equals(command.trainingEligible())) {
                throw validationProblem("Notification preferences are never training eligible");
            }
            if (Boolean.FALSE.equals(command.accountSecurityEnabled())) {
                throw validationProblem("Account security notifications are mandatory and cannot be disabled");
            }

            Instant now = clock.instant();
            preferenceRepository.lockUser(userId);

            Optional<NotificationPreferenceSnapshot> existingOpt = preferenceRepository.findByUserId(userId);
            NotificationPreferenceSnapshot current = existingOpt
                .orElseGet(() -> NotificationPreferenceSnapshot.defaultFor(userId, properties.defaultDailyCap(), now));

            if (current.version() != command.expectedVersion()) {
                metrics.recordPreferenceUpdate("conflict");
                throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.OPTIMISTIC_CONFLICT,
                    "Notification preferences changed since they were loaded",
                    true,
                    "REFRESH"
                );
            }

            boolean pushEnabled = command.pushEnabled() != null ? command.pushEnabled() : current.pushEnabled();
            boolean studyPlanEnabled = command.studyPlanEnabled() != null
                ? command.studyPlanEnabled()
                : current.studyPlanEnabled();
            boolean spacedReviewEnabled = command.spacedReviewEnabled() != null
                ? command.spacedReviewEnabled()
                : current.spacedReviewEnabled();
            boolean examDeadlineEnabled = command.examDeadlineEnabled() != null
                ? command.examDeadlineEnabled()
                : current.examDeadlineEnabled();
            boolean billingTrialReminderEnabled = command.billingTrialReminderEnabled() != null
                ? command.billingTrialReminderEnabled()
                : current.billingTrialReminderEnabled();
            boolean quietHoursEnabled = command.quietHoursEnabled() != null
                ? command.quietHoursEnabled()
                : current.quietHoursEnabled();

            LocalTime quietStart = command.quietHoursStartLocal() != null
                ? QuietHoursPolicy.parseLocalTime(command.quietHoursStartLocal())
                : current.quietHoursStartLocal();
            LocalTime quietEnd = command.quietHoursEndLocal() != null
                ? QuietHoursPolicy.parseLocalTime(command.quietHoursEndLocal())
                : current.quietHoursEndLocal();
            QuietHoursPolicy.validateWindow(quietStart, quietEnd);

            String timezone = command.timezone() != null
                ? QuietHoursPolicy.parseAndValidateZone(command.timezone()).getId()
                : ZoneId.of(current.timezone()).getId();

            int dailyCap = command.dailyCap() != null
                ? validateDailyCap(command.dailyCap())
                : current.dailyCap();

            long nextVersion = current.version() + 1;
            NotificationPreferenceSnapshot updated = new NotificationPreferenceSnapshot(
                userId,
                pushEnabled,
                studyPlanEnabled,
                spacedReviewEnabled,
                examDeadlineEnabled,
                billingTrialReminderEnabled,
                true,
                quietHoursEnabled,
                quietStart,
                quietEnd,
                timezone,
                dailyCap,
                nextVersion,
                current.createdAt(),
                now
            );

            if (existingOpt.isEmpty()) {
                preferenceRepository.insertInitial(updated);
            } else {
                int affected = preferenceRepository.updateOptimistic(updated, command.expectedVersion());
                if (affected == 0) {
                    metrics.recordPreferenceUpdate("conflict");
                    throw new ApiProblemException(
                        HttpStatus.CONFLICT,
                        ApiErrorCode.OPTIMISTIC_CONFLICT,
                        "Notification preferences changed since they were loaded",
                        true,
                        "REFRESH"
                    );
                }
            }

            metrics.recordPreferenceUpdate("success");
            return toView(updated);
        } catch (ApiProblemException exception) {
            if (exception.code() != ApiErrorCode.OPTIMISTIC_CONFLICT) {
                metrics.recordPreferenceUpdate("invalid");
            }
            throw exception;
        }
    }

    private int validateDailyCap(int dailyCap) {
        if (dailyCap < 0 || dailyCap > properties.maxDailyCap()) {
            throw validationProblem("Notification dailyCap must be between 0 and " + properties.maxDailyCap());
        }
        return dailyCap;
    }

    private void requireActiveAccount(UUID userId) {
        if (!preferenceRepository.isAccountActive(userId)) {
            throw new ApiProblemException(
                HttpStatus.FORBIDDEN,
                ApiErrorCode.ACCOUNT_NOT_ACTIVE,
                "Account is not active",
                false,
                "SIGN_IN"
            );
        }
    }

    private void requireFeatureEnabled() {
        if (!properties.enabled()) {
            throw new ApiProblemException(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.NOTIFICATION_FEATURE_DISABLED,
                "Notification infrastructure is disabled",
                true,
                "RETRY_LATER"
            );
        }
    }

    private static NotificationPreferenceView toView(NotificationPreferenceSnapshot snapshot) {
        return new NotificationPreferenceView(
            SCHEMA_VERSION,
            snapshot.userId(),
            snapshot.pushEnabled(),
            snapshot.studyPlanEnabled(),
            snapshot.spacedReviewEnabled(),
            snapshot.examDeadlineEnabled(),
            snapshot.billingTrialReminderEnabled(),
            snapshot.accountSecurityEnabled(),
            snapshot.quietHoursEnabled(),
            QuietHoursPolicy.formatLocalTime(snapshot.quietHoursStartLocal()),
            QuietHoursPolicy.formatLocalTime(snapshot.quietHoursEndLocal()),
            snapshot.timezone(),
            snapshot.dailyCap(),
            snapshot.version(),
            false,
            snapshot.updatedAt()
        );
    }

    private static ApiProblemException validationProblem(String title) {
        return new ApiProblemException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.NOTIFICATION_VALIDATION_FAILED,
            title,
            true,
            "RETRY"
        );
    }
}
