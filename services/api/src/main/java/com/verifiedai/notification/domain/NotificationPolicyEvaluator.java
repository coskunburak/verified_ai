package com.verifiedai.notification.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import org.springframework.http.HttpStatus;

/**
 * Pure domain decision engine evaluating opt-in, quiet hours, daily cap, expiry, and device readiness.
 */
public final class NotificationPolicyEvaluator {

    public PolicyDecision evaluate(PolicyEvaluationInput input) {
        Objects.requireNonNull(input, "policy evaluation input must not be null");
        if (!input.intentType().matchesCategory(input.category())) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.NOTIFICATION_VALIDATION_FAILED,
                "Notification intentType " + input.intentType() + " does not belong to category " + input.category(),
                true,
                "RETRY"
            );
        }
        if (!input.expiresAt().isAfter(input.scheduledFor())) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.NOTIFICATION_VALIDATION_FAILED,
                "Notification expiresAt must be strictly after scheduledFor",
                true,
                "RETRY"
            );
        }

        if (!input.featureEnabled()) {
            return suppressed(NotificationSuppressionReason.FEATURE_DISABLED, input.scheduledFor());
        }
        if (!input.accountActive()) {
            return suppressed(NotificationSuppressionReason.ACCOUNT_NOT_ACTIVE, input.scheduledFor());
        }
        if (!input.now().isBefore(input.expiresAt())) {
            return suppressed(NotificationSuppressionReason.INTENT_EXPIRED, input.scheduledFor());
        }

        if (input.category().isSecurityMandatory()) {
            if (!input.hasDeliverableActiveDevice()) {
                return suppressed(NotificationSuppressionReason.NO_ACTIVE_DEVICE, input.scheduledFor());
            }
            return new PolicyDecision(
                NotificationDeliveryStatus.SCHEDULED,
                null,
                input.scheduledFor(),
                "SECURITY_MANDATORY_SCHEDULED"
            );
        }

        NotificationPreferenceSnapshot pref = input.preferences();
        if (!pref.pushEnabled()) {
            return suppressed(NotificationSuppressionReason.PUSH_GLOBALLY_DISABLED, input.scheduledFor());
        }
        if (!pref.isCategoryEnabled(input.category())) {
            return suppressed(NotificationSuppressionReason.USER_OPTED_OUT, input.scheduledFor());
        }
        if (!input.hasDeliverableActiveDevice()) {
            return suppressed(NotificationSuppressionReason.NO_ACTIVE_DEVICE, input.scheduledFor());
        }
        if (input.sentTodayNonSecurityCount() >= pref.dailyCap()) {
            return suppressed(NotificationSuppressionReason.DAILY_CAP_REACHED, input.scheduledFor());
        }

        ZoneId zoneId = QuietHoursPolicy.parseAndValidateZone(pref.timezone());
        Instant effectiveReference = input.scheduledFor().isBefore(input.now())
            ? input.now()
            : input.scheduledFor();
        QuietHoursPolicy.QuietHoursEvaluation quietEval = QuietHoursPolicy.evaluate(
            effectiveReference,
            input.expiresAt(),
            pref.quietHoursEnabled(),
            pref.quietHoursStartLocal(),
            pref.quietHoursEndLocal(),
            zoneId
        );

        if (quietEval.expired()) {
            return suppressed(NotificationSuppressionReason.INTENT_EXPIRED, input.scheduledFor());
        }
        if (quietEval.deferred()) {
            return new PolicyDecision(
                NotificationDeliveryStatus.DEFERRED_QUIET_HOURS,
                null,
                quietEval.effectiveSendAt(),
                "QUIET_HOURS_DEFERRED"
            );
        }
        return new PolicyDecision(
            NotificationDeliveryStatus.SCHEDULED,
            null,
            input.scheduledFor(),
            "SCHEDULED"
        );
    }

    public PolicyDecision evaluateAtDispatch(
        boolean featureEnabled,
        boolean accountActive,
        NotificationCategory category,
        NotificationIntentType intentType,
        NotificationPreferenceSnapshot preferences,
        boolean hasDeliverableActiveDevice,
        int sentTodayNonSecurityCount,
        Instant now,
        Instant expiresAt
    ) {
        if (!featureEnabled) {
            return suppressed(NotificationSuppressionReason.FEATURE_DISABLED, now);
        }
        if (!accountActive) {
            return suppressed(NotificationSuppressionReason.ACCOUNT_NOT_ACTIVE, now);
        }
        if (!now.isBefore(expiresAt)) {
            return suppressed(NotificationSuppressionReason.INTENT_EXPIRED, now);
        }
        return evaluate(new PolicyEvaluationInput(
            featureEnabled,
            accountActive,
            category,
            intentType,
            preferences,
            hasDeliverableActiveDevice,
            sentTodayNonSecurityCount,
            now,
            now,
            expiresAt
        ));
    }

    private static PolicyDecision suppressed(NotificationSuppressionReason reason, Instant effectiveSendAt) {
        return new PolicyDecision(
            NotificationDeliveryStatus.SUPPRESSED,
            reason,
            effectiveSendAt,
            reason.name()
        );
    }

    public record PolicyEvaluationInput(
        boolean featureEnabled,
        boolean accountActive,
        NotificationCategory category,
        NotificationIntentType intentType,
        NotificationPreferenceSnapshot preferences,
        boolean hasDeliverableActiveDevice,
        int sentTodayNonSecurityCount,
        Instant now,
        Instant scheduledFor,
        Instant expiresAt
    ) {
    }

    public record PolicyDecision(
        NotificationDeliveryStatus status,
        NotificationSuppressionReason suppressionReason,
        Instant effectiveSendAt,
        String reasonCode
    ) {
    }
}
