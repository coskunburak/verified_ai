package com.verifiedai.notification.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * Canonical registry of Sprint 10.5 lifecycle notification templates and strict PII/parameter allowlists (INV-LIFE-004).
 */
public final class LifecycleTemplateRegistry {

    public static final String TEMPLATE_STUDY_PLAN_DAILY_MISSION = "notification.template.study_plan.daily_mission";
    public static final String TEMPLATE_SPACED_REVIEW_DUE = "notification.template.spaced_review.due";
    public static final String TEMPLATE_EXAM_DEADLINE_COUNTDOWN = "notification.template.exam_deadline.countdown";
    public static final String TEMPLATE_BILLING_TRIAL_ENDING_48H = "notification.template.billing.trial_ending_48h";
    public static final String TEMPLATE_ACCOUNT_SECURITY_ALERT = "notification.template.account.security_alert";

    public static final Set<String> ALLOWED_SECURITY_EVENT_TYPES = Set.of(
        "REFRESH_TOKEN_REUSE",
        "ACCOUNT_DELETION_REQUESTED",
        "SESSION_REVOKED_ALL"
    );

    private static final List<LifecycleTemplateSpec> CANONICAL_TEMPLATES = List.of(
        new LifecycleTemplateSpec(
            NotificationCategory.STUDY_PLAN,
            NotificationIntentType.STUDY_PLAN_DAILY_MISSION,
            TEMPLATE_STUDY_PLAN_DAILY_MISSION,
            Set.of("budgetMinutes", "remainingTasks"),
            "verified-ai://study-plan/today",
            20,
            "studyplan"
        ),
        new LifecycleTemplateSpec(
            NotificationCategory.SPACED_REVIEW,
            NotificationIntentType.SPACED_REVIEW_DUE,
            TEMPLATE_SPACED_REVIEW_DUE,
            Set.of("dueCount", "triggerKind"),
            "verified-ai://spaced-review",
            24,
            "studyplan"
        ),
        new LifecycleTemplateSpec(
            NotificationCategory.EXAM_DEADLINE,
            NotificationIntentType.EXAM_DEADLINE_COUNTDOWN,
            TEMPLATE_EXAM_DEADLINE_COUNTDOWN,
            Set.of("examCode", "daysRemaining"),
            "verified-ai://exam-readiness",
            24,
            "exam"
        ),
        new LifecycleTemplateSpec(
            NotificationCategory.BILLING_TRIAL_REMINDER,
            NotificationIntentType.TRIAL_ENDING_48H_REMINDER,
            TEMPLATE_BILLING_TRIAL_ENDING_48H,
            Set.of("hoursRemaining", "planCode"),
            "verified-ai://subscription/manage",
            0,
            "billing"
        ),
        new LifecycleTemplateSpec(
            NotificationCategory.ACCOUNT_SECURITY,
            NotificationIntentType.ACCOUNT_SECURITY_ALERT,
            TEMPLATE_ACCOUNT_SECURITY_ALERT,
            Set.of("eventType"),
            "verified-ai://account/security",
            0,
            "identity"
        )
    );

    private static final Map<String, LifecycleTemplateSpec> BY_TEMPLATE_KEY = Map.of(
        TEMPLATE_STUDY_PLAN_DAILY_MISSION, CANONICAL_TEMPLATES.get(0),
        TEMPLATE_SPACED_REVIEW_DUE, CANONICAL_TEMPLATES.get(1),
        TEMPLATE_EXAM_DEADLINE_COUNTDOWN, CANONICAL_TEMPLATES.get(2),
        TEMPLATE_BILLING_TRIAL_ENDING_48H, CANONICAL_TEMPLATES.get(3),
        TEMPLATE_ACCOUNT_SECURITY_ALERT, CANONICAL_TEMPLATES.get(4)
    );

    private LifecycleTemplateRegistry() {
    }

    public static List<LifecycleTemplateSpec> allTemplates() {
        return CANONICAL_TEMPLATES;
    }

    public static Optional<LifecycleTemplateSpec> findByTemplateKey(String templateKey) {
        if (templateKey == null || templateKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_TEMPLATE_KEY.get(templateKey.trim()));
    }

    public static LifecycleTemplateSpec validate(
        NotificationCategory category,
        NotificationIntentType intentType,
        String templateKey,
        Map<String, String> templateParams,
        String deepLinkPath
    ) {
        if (category == null || intentType == null || templateKey == null || templateKey.isBlank()) {
            throw validationFailed("Lifecycle notification category, intentType, and templateKey are required");
        }
        if (!intentType.matchesCategory(category)) {
            throw validationFailed("Lifecycle notification intentType does not match category");
        }
        LifecycleTemplateSpec spec = BY_TEMPLATE_KEY.get(templateKey.trim());
        if (spec == null) {
            throw validationFailed("Unknown lifecycle notification templateKey: " + templateKey);
        }
        if (spec.category() != category || spec.intentType() != intentType) {
            throw validationFailed("Lifecycle templateKey does not match category/intentType: " + templateKey);
        }
        if (deepLinkPath == null || !spec.expectedDeepLinkPath().equals(deepLinkPath)) {
            throw validationFailed("Lifecycle deepLinkPath does not match canonical route for " + templateKey);
        }

        // Enforce Sprint 10.4 PII denylist and byte/length limits before checking strict allowlist keys.
        NotificationPayloadHasher.canonicalizeParams(templateParams);

        Map<String, String> safeParams = templateParams == null ? Map.of() : templateParams;
        for (String key : safeParams.keySet()) {
            if (!spec.allowedParamKeys().contains(key)) {
                throw validationFailed("Disallowed template parameter key '" + key + "' for template " + templateKey);
            }
        }
        if (intentType == NotificationIntentType.ACCOUNT_SECURITY_ALERT) {
            String eventType = safeParams.get("eventType");
            if (eventType == null || !ALLOWED_SECURITY_EVENT_TYPES.contains(eventType)) {
                throw validationFailed("Security alert requires an allowlisted eventType parameter");
            }
        }
        return spec;
    }

    private static ApiProblemException validationFailed(String message) {
        return new ApiProblemException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.NOTIFICATION_VALIDATION_FAILED,
            message,
            true,
            "RETRY"
        );
    }

    public record LifecycleTemplateSpec(
        NotificationCategory category,
        NotificationIntentType intentType,
        String templateKey,
        Set<String> allowedParamKeys,
        String expectedDeepLinkPath,
        int defaultCooldownHours,
        String sourceModule
    ) {
    }
}
