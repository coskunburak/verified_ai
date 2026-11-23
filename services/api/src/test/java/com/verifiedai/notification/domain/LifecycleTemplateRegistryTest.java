package com.verifiedai.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class LifecycleTemplateRegistryTest {

    @Test
    void validatesAllFiveCanonicalTemplatesWithStrictAllowlists() {
        assertThat(LifecycleTemplateRegistry.allTemplates()).hasSize(5);

        LifecycleTemplateRegistry.validate(
            NotificationCategory.STUDY_PLAN,
            NotificationIntentType.STUDY_PLAN_DAILY_MISSION,
            LifecycleTemplateRegistry.TEMPLATE_STUDY_PLAN_DAILY_MISSION,
            Map.of("budgetMinutes", "15", "remainingTasks", "3"),
            "verified-ai://study-plan/today"
        );

        LifecycleTemplateRegistry.validate(
            NotificationCategory.SPACED_REVIEW,
            NotificationIntentType.SPACED_REVIEW_DUE,
            LifecycleTemplateRegistry.TEMPLATE_SPACED_REVIEW_DUE,
            Map.of("dueCount", "2", "triggerKind", "TRAP_IMMUNITY_7D"),
            "verified-ai://spaced-review"
        );

        LifecycleTemplateRegistry.validate(
            NotificationCategory.EXAM_DEADLINE,
            NotificationIntentType.EXAM_DEADLINE_COUNTDOWN,
            LifecycleTemplateRegistry.TEMPLATE_EXAM_DEADLINE_COUNTDOWN,
            Map.of("examCode", "TR_YKS_TYT_MATH", "daysRemaining", "14"),
            "verified-ai://exam-readiness"
        );

        LifecycleTemplateRegistry.validate(
            NotificationCategory.BILLING_TRIAL_REMINDER,
            NotificationIntentType.TRIAL_ENDING_48H_REMINDER,
            LifecycleTemplateRegistry.TEMPLATE_BILLING_TRIAL_ENDING_48H,
            Map.of("hoursRemaining", "48", "planCode", "coach_pro_annual"),
            "verified-ai://subscription/manage"
        );

        LifecycleTemplateRegistry.validate(
            NotificationCategory.ACCOUNT_SECURITY,
            NotificationIntentType.ACCOUNT_SECURITY_ALERT,
            LifecycleTemplateRegistry.TEMPLATE_ACCOUNT_SECURITY_ALERT,
            Map.of("eventType", "REFRESH_TOKEN_REUSE"),
            "verified-ai://account/security"
        );
    }

    @Test
    void rejectsUnknownTemplatePiiKeysDisallowedParamsSpoofedDeepLinkAndMismatchedCategory() {
        assertValidationFailure(() -> LifecycleTemplateRegistry.validate(
            NotificationCategory.STUDY_PLAN,
            NotificationIntentType.STUDY_PLAN_DAILY_MISSION,
            "notification.template.unknown.spam",
            Map.of(),
            "verified-ai://study-plan/today"
        ));

        assertValidationFailure(() -> LifecycleTemplateRegistry.validate(
            NotificationCategory.STUDY_PLAN,
            NotificationIntentType.SPACED_REVIEW_DUE,
            LifecycleTemplateRegistry.TEMPLATE_STUDY_PLAN_DAILY_MISSION,
            Map.of("budgetMinutes", "15"),
            "verified-ai://study-plan/today"
        ));

        assertValidationFailure(() -> LifecycleTemplateRegistry.validate(
            NotificationCategory.STUDY_PLAN,
            NotificationIntentType.STUDY_PLAN_DAILY_MISSION,
            LifecycleTemplateRegistry.TEMPLATE_STUDY_PLAN_DAILY_MISSION,
            Map.of("studentName", "Ayse", "budgetMinutes", "15"),
            "verified-ai://study-plan/today"
        ));

        assertValidationFailure(() -> LifecycleTemplateRegistry.validate(
            NotificationCategory.STUDY_PLAN,
            NotificationIntentType.STUDY_PLAN_DAILY_MISSION,
            LifecycleTemplateRegistry.TEMPLATE_STUDY_PLAN_DAILY_MISSION,
            Map.of("unexpectedExtra", "15"),
            "verified-ai://study-plan/today"
        ));

        assertValidationFailure(() -> LifecycleTemplateRegistry.validate(
            NotificationCategory.BILLING_TRIAL_REMINDER,
            NotificationIntentType.TRIAL_ENDING_48H_REMINDER,
            LifecycleTemplateRegistry.TEMPLATE_BILLING_TRIAL_ENDING_48H,
            Map.of("hoursRemaining", "48", "planCode", "coach_pro_annual"),
            "https://evil.example/subscription/manage"
        ));

        assertValidationFailure(() -> LifecycleTemplateRegistry.validate(
            NotificationCategory.ACCOUNT_SECURITY,
            NotificationIntentType.ACCOUNT_SECURITY_ALERT,
            LifecycleTemplateRegistry.TEMPLATE_ACCOUNT_SECURITY_ALERT,
            Map.of("eventType", "UNLISTED_EVENT"),
            "verified-ai://account/security"
        ));
    }

    private static void assertValidationFailure(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                .isEqualTo(ApiErrorCode.NOTIFICATION_VALIDATION_FAILED));
    }
}
