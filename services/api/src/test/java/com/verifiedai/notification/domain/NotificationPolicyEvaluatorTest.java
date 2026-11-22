package com.verifiedai.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.notification.domain.NotificationPolicyEvaluator.PolicyDecision;
import com.verifiedai.notification.domain.NotificationPolicyEvaluator.PolicyEvaluationInput;
import com.verifiedai.notification.infrastructure.NotificationProperties;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.time.Instant;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class NotificationPolicyEvaluatorTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-000000000101");
    private static final Instant DAY_NOW = Instant.parse("2026-11-22T12:00:00Z");
    private static final Instant QUIET_NOW = Instant.parse("2026-11-22T23:00:00Z");
    private final NotificationPolicyEvaluator evaluator = new NotificationPolicyEvaluator();

    @Test
    void suppressesNonSecurityWhenFeatureDisabledOrDefaultOptOutOrDailyCapReached() {
        NotificationPreferenceSnapshot defaults = NotificationPreferenceSnapshot.defaultFor(USER_ID, DAY_NOW);
        assertThat(defaults.pushEnabled()).isFalse();
        assertThat(defaults.studyPlanEnabled()).isFalse();
        assertThat(defaults.spacedReviewEnabled()).isFalse();
        assertThat(defaults.examDeadlineEnabled()).isFalse();
        assertThat(defaults.billingTrialReminderEnabled()).isFalse();
        assertThat(defaults.accountSecurityEnabled()).isTrue();

        PolicyDecision disabled = evaluator.evaluate(new PolicyEvaluationInput(
            false,
            true,
            NotificationCategory.STUDY_PLAN,
            NotificationIntentType.STUDY_PLAN_DAILY_MISSION,
            defaults,
            true,
            0,
            DAY_NOW,
            DAY_NOW,
            DAY_NOW.plusSeconds(3600)
        ));
        assertThat(disabled.status()).isEqualTo(NotificationDeliveryStatus.SUPPRESSED);
        assertThat(disabled.suppressionReason()).isEqualTo(NotificationSuppressionReason.FEATURE_DISABLED);

        PolicyDecision pushOff = evaluator.evaluate(new PolicyEvaluationInput(
            true,
            true,
            NotificationCategory.STUDY_PLAN,
            NotificationIntentType.STUDY_PLAN_DAILY_MISSION,
            defaults,
            true,
            0,
            DAY_NOW,
            DAY_NOW,
            DAY_NOW.plusSeconds(3600)
        ));
        assertThat(pushOff.status()).isEqualTo(NotificationDeliveryStatus.SUPPRESSED);
        assertThat(pushOff.suppressionReason()).isEqualTo(NotificationSuppressionReason.PUSH_GLOBALLY_DISABLED);

        NotificationPreferenceSnapshot pushOnTrialOff = new NotificationPreferenceSnapshot(
            USER_ID, true, true, false, false, false, true,
            true, LocalTime.of(22, 0), LocalTime.of(8, 0), "UTC", 2, 1L, DAY_NOW, DAY_NOW
        );
        PolicyDecision trialOptOut = evaluator.evaluate(new PolicyEvaluationInput(
            true,
            true,
            NotificationCategory.BILLING_TRIAL_REMINDER,
            NotificationIntentType.TRIAL_ENDING_48H_REMINDER,
            pushOnTrialOff,
            true,
            0,
            DAY_NOW,
            DAY_NOW,
            DAY_NOW.plusSeconds(3600)
        ));
        assertThat(trialOptOut.status()).isEqualTo(NotificationDeliveryStatus.SUPPRESSED);
        assertThat(trialOptOut.suppressionReason()).isEqualTo(NotificationSuppressionReason.USER_OPTED_OUT);

        PolicyDecision capped = evaluator.evaluate(new PolicyEvaluationInput(
            true,
            true,
            NotificationCategory.STUDY_PLAN,
            NotificationIntentType.STUDY_PLAN_DAILY_MISSION,
            pushOnTrialOff,
            true,
            2,
            DAY_NOW,
            DAY_NOW,
            DAY_NOW.plusSeconds(3600)
        ));
        assertThat(capped.status()).isEqualTo(NotificationDeliveryStatus.SUPPRESSED);
        assertThat(capped.suppressionReason()).isEqualTo(NotificationSuppressionReason.DAILY_CAP_REACHED);
    }

    @Test
    void accountSecurityBypassesOptOutQuietHoursAndDailyCapUnlessNoDevice() {
        NotificationPreferenceSnapshot defaults = NotificationPreferenceSnapshot.defaultFor(USER_ID, QUIET_NOW);

        PolicyDecision allowed = evaluator.evaluate(new PolicyEvaluationInput(
            true,
            true,
            NotificationCategory.ACCOUNT_SECURITY,
            NotificationIntentType.ACCOUNT_SECURITY_ALERT,
            defaults,
            true,
            5,
            QUIET_NOW,
            QUIET_NOW,
            QUIET_NOW.plusSeconds(3600)
        ));
        assertThat(allowed.status()).isEqualTo(NotificationDeliveryStatus.SCHEDULED);
        assertThat(allowed.suppressionReason()).isNull();

        PolicyDecision noDevice = evaluator.evaluate(new PolicyEvaluationInput(
            true,
            true,
            NotificationCategory.ACCOUNT_SECURITY,
            NotificationIntentType.ACCOUNT_SECURITY_ALERT,
            defaults,
            false,
            0,
            QUIET_NOW,
            QUIET_NOW,
            QUIET_NOW.plusSeconds(3600)
        ));
        assertThat(noDevice.status()).isEqualTo(NotificationDeliveryStatus.SUPPRESSED);
        assertThat(noDevice.suppressionReason()).isEqualTo(NotificationSuppressionReason.NO_ACTIVE_DEVICE);
    }

    @Test
    void rejectsCategoryIntentMismatchPiiTemplateParamsAndProducesOrderInvariantHash() {
        NotificationPreferenceSnapshot defaults = NotificationPreferenceSnapshot.defaultFor(USER_ID, DAY_NOW);
        assertThatThrownBy(() -> evaluator.evaluate(new PolicyEvaluationInput(
            true,
            true,
            NotificationCategory.STUDY_PLAN,
            NotificationIntentType.TRIAL_ENDING_48H_REMINDER,
            defaults,
            true,
            0,
            DAY_NOW,
            DAY_NOW,
            DAY_NOW.plusSeconds(3600)
        )))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                .isEqualTo(ApiErrorCode.NOTIFICATION_VALIDATION_FAILED));

        for (String piiKey : new String[]{"email", "phone", "deviceToken", "rawProblemText", "studentName", "jwt"}) {
            assertThatThrownBy(() -> NotificationPayloadHasher.canonicalizeParams(Map.of(piiKey, "sensitive")))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                    .isEqualTo(ApiErrorCode.NOTIFICATION_VALIDATION_FAILED));
        }

        assertThatThrownBy(() -> NotificationPayloadHasher.validateScheduleFields(
            "idem-key-1234", "tr", "billing.trial.48h", Map.of(),
            "https://evil.example/phish", "billing", "corr-1"
        ))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                .isEqualTo(ApiErrorCode.NOTIFICATION_VALIDATION_FAILED));

        Map<String, String> orderA = new LinkedHashMap<>();
        orderA.put("hoursRemaining", "48");
        orderA.put("planCode", "PRO");

        Map<String, String> orderB = new LinkedHashMap<>();
        orderB.put("planCode", "PRO");
        orderB.put("hoursRemaining", "48");

        String jsonA = NotificationPayloadHasher.canonicalizeParams(orderA);
        String jsonB = NotificationPayloadHasher.canonicalizeParams(orderB);
        assertThat(jsonA).isEqualTo(jsonB);

        String hashA = NotificationPayloadHasher.computePayloadHash(
            NotificationCategory.BILLING_TRIAL_REMINDER,
            NotificationIntentType.TRIAL_ENDING_48H_REMINDER,
            NotificationChannel.PUSH,
            "tr",
            "billing.trial.ending.48h",
            jsonA,
            "verified-ai://subscription/manage",
            DAY_NOW,
            DAY_NOW.plusSeconds(3600)
        );
        String hashB = NotificationPayloadHasher.computePayloadHash(
            NotificationCategory.BILLING_TRIAL_REMINDER,
            NotificationIntentType.TRIAL_ENDING_48H_REMINDER,
            NotificationChannel.PUSH,
            "tr",
            "billing.trial.ending.48h",
            jsonB,
            "verified-ai://subscription/manage",
            DAY_NOW,
            DAY_NOW.plusSeconds(3600)
        );
        assertThat(hashA).isEqualTo(hashB).hasSize(64);
    }

    @Test
    void notificationPropertiesFailClosedOnLiveApnsFlagOrOutOfRangeSettings() {
        assertThatThrownBy(() -> new NotificationProperties(true, false, true, 50, 15_000L, 60, 2, 5, 3, 60, 10))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("apns-live-delivery-enabled=false");

        assertThatThrownBy(() -> new NotificationProperties(true, false, false, 201, 15_000L, 60, 2, 5, 3, 60, 10))
            .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> new NotificationProperties(true, false, false, 50, 15_000L, 60, 6, 5, 3, 60, 10))
            .isInstanceOf(IllegalStateException.class);
    }
}
