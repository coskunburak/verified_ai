package com.verifiedai.analytics.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * Closed canonical analytics event registry (INV-ANL-001, INV-ANL-002).
 * Generic UI names (e.g., button_clicked, tap, click, screen_viewed) are intentionally excluded and fail closed.
 */
public final class AnalyticsEventCatalog {

    public static final String CATALOG_VERSION = "1.0.0";

    private static final Set<String> PROPS_ONBOARDING_AUTH = Set.of(
        "authMethod", "locale", "educationLevel", "dailyBudgetMinutes", "latencyBucket"
    );
    private static final Set<String> PROPS_CAPTURE_CLIENT = Set.of(
        "captureSource", "assetKind", "qualityWarningCode", "latencyBucket"
    );
    private static final Set<String> PROPS_INGESTION_PIPELINE = Set.of(
        "assetKind", "captureSource", "status", "supportStatus", "unsupportedReason",
        "reviewRequired", "routeFamily", "providerFamily", "schemaVersion",
        "retryClass", "latencyBucket", "costBucket"
    );
    private static final Set<String> PROPS_FIRST_SOLUTION = Set.of(
        "problemType", "primarySkillCode", "difficultyBand", "verificationStatus",
        "latencyBucket", "entitlementTier"
    );
    private static final Set<String> PROPS_SOLVE_SERVER = Set.of(
        "problemType", "primarySkillCode", "difficultyBand", "verificationStatus",
        "latencyBucket", "costBucket", "entitlementTier", "failureClass"
    );
    private static final Set<String> PROPS_SOLVE_CLIENT = Set.of(
        "problemType", "primarySkillCode", "difficultyBand", "verificationStatus", "entitlementTier"
    );
    private static final Set<String> PROPS_TUTOR = Set.of(
        "hintLevel", "pedagogicalPhase", "turnCountBucket", "entitlementTier", "latencyBucket"
    );
    private static final Set<String> PROPS_LEARNING_CORE = Set.of(
        "canonicalSkillCode", "mistakeCode", "fromBand", "toBand",
        "evidenceClass", "slotRole", "durationBucket", "entitlementTier"
    );
    private static final Set<String> PROPS_LEARNING_OS_V7 = Set.of(
        "fromState", "toState", "errorCode", "canonicalSkillCode", "variant",
        "evidenceClass", "passed", "durationMs", "stage", "passedIndependent",
        "budgetMinutes", "triggerType"
    );
    private static final Set<String> PROPS_EXAM = Set.of(
        "examCode", "jurisdiction", "confidenceTier", "mode", "durationBucket", "entitlementTier"
    );
    private static final Set<String> PROPS_BILLING_CLIENT_OR_SERVER = Set.of(
        "placement", "packageCode", "tierCode", "storefront", "failureReason", "entitlementTier"
    );
    private static final Set<String> PROPS_BILLING_SERVER_ONLY = Set.of(
        "packageCode", "fromTier", "toTier", "reason", "isFreeTrial", "entitlementTier"
    );
    private static final Set<String> PROPS_LIFECYCLE = Set.of(
        "category", "intentType", "weekIso", "locale", "entitlementTier"
    );

    private static final Map<String, AnalyticsEventSpec> REGISTRY;
    private static final List<AnalyticsEventSpec> ORDERED_SPECS;

    static {
        Map<String, AnalyticsEventSpec> specs = new LinkedHashMap<>();

        // ACQUISITION_ACTIVATION (14 events)
        register(specs, "onboarding_started", AnalyticsEventDomain.ACQUISITION_ACTIVATION, true, true, PROPS_ONBOARDING_AUTH);
        register(specs, "onboarding_completed", AnalyticsEventDomain.ACQUISITION_ACTIVATION, true, true, PROPS_ONBOARDING_AUTH);
        register(specs, "sign_in_completed", AnalyticsEventDomain.ACQUISITION_ACTIVATION, true, true, PROPS_ONBOARDING_AUTH);
        register(specs, "problem_capture_started", AnalyticsEventDomain.ACQUISITION_ACTIVATION, true, false, PROPS_CAPTURE_CLIENT);
        register(specs, "problem_capture_source_selected", AnalyticsEventDomain.ACQUISITION_ACTIVATION, true, false, PROPS_CAPTURE_CLIENT);
        register(specs, "problem_capture_quality_warning", AnalyticsEventDomain.ACQUISITION_ACTIVATION, true, false, PROPS_CAPTURE_CLIENT);
        register(specs, "problem_capture_accepted", AnalyticsEventDomain.ACQUISITION_ACTIVATION, true, false, PROPS_CAPTURE_CLIENT);
        register(specs, "problem_asset_uploaded", AnalyticsEventDomain.ACQUISITION_ACTIVATION, false, true, PROPS_INGESTION_PIPELINE);
        register(specs, "problem_recognition_completed", AnalyticsEventDomain.ACQUISITION_ACTIVATION, false, true, PROPS_INGESTION_PIPELINE);
        register(specs, "problem_parse_completed", AnalyticsEventDomain.ACQUISITION_ACTIVATION, false, true, PROPS_INGESTION_PIPELINE);
        register(specs, "problem_parse_review_required", AnalyticsEventDomain.ACQUISITION_ACTIVATION, false, true, PROPS_INGESTION_PIPELINE);
        register(specs, "problem_parse_unsupported", AnalyticsEventDomain.ACQUISITION_ACTIVATION, false, true, PROPS_INGESTION_PIPELINE);
        register(specs, "first_solution_completed", AnalyticsEventDomain.ACQUISITION_ACTIVATION, false, true, PROPS_FIRST_SOLUTION);
        register(specs, "first_verified_solution_viewed", AnalyticsEventDomain.ACQUISITION_ACTIVATION, true, false, PROPS_FIRST_SOLUTION);

        // SOLVING (6 events)
        register(specs, "solve_requested", AnalyticsEventDomain.SOLVING, false, true, PROPS_SOLVE_SERVER);
        register(specs, "solve_completed", AnalyticsEventDomain.SOLVING, false, true, PROPS_SOLVE_SERVER);
        register(specs, "solve_failed", AnalyticsEventDomain.SOLVING, false, true, PROPS_SOLVE_SERVER);
        register(specs, "parse_corrected", AnalyticsEventDomain.SOLVING, false, true, PROPS_SOLVE_SERVER);
        register(specs, "verification_details_viewed", AnalyticsEventDomain.SOLVING, true, false, PROPS_SOLVE_CLIENT);
        register(specs, "solution_alternative_viewed", AnalyticsEventDomain.SOLVING, true, false, PROPS_SOLVE_CLIENT);

        // TUTOR (5 events)
        register(specs, "tutor_session_started", AnalyticsEventDomain.TUTOR, true, true, PROPS_TUTOR);
        register(specs, "tutor_turn_submitted", AnalyticsEventDomain.TUTOR, true, true, PROPS_TUTOR);
        register(specs, "hint_requested", AnalyticsEventDomain.TUTOR, true, true, PROPS_TUTOR);
        register(specs, "answer_revealed", AnalyticsEventDomain.TUTOR, true, true, PROPS_TUTOR);
        register(specs, "tutor_session_completed", AnalyticsEventDomain.TUTOR, true, true, PROPS_TUTOR);

        // LEARNING_MASTERY — core (7 events) + V7 Learning OS (9 events)
        register(specs, "attempt_submitted", AnalyticsEventDomain.LEARNING_MASTERY, false, true, PROPS_LEARNING_CORE);
        register(specs, "mistake_detected", AnalyticsEventDomain.LEARNING_MASTERY, false, true, PROPS_LEARNING_CORE);
        register(specs, "mistake_reviewed", AnalyticsEventDomain.LEARNING_MASTERY, true, false, PROPS_LEARNING_CORE);
        register(specs, "practice_session_started", AnalyticsEventDomain.LEARNING_MASTERY, true, false, PROPS_LEARNING_CORE);
        register(specs, "practice_session_completed", AnalyticsEventDomain.LEARNING_MASTERY, false, true, PROPS_LEARNING_CORE);
        register(specs, "mastery_band_changed", AnalyticsEventDomain.LEARNING_MASTERY, false, true, PROPS_LEARNING_CORE);
        register(specs, "study_plan_item_completed", AnalyticsEventDomain.LEARNING_MASTERY, false, true, PROPS_LEARNING_CORE);

        register(specs, "mistake_passport.item_created", AnalyticsEventDomain.LEARNING_MASTERY, false, true, PROPS_LEARNING_OS_V7);
        register(specs, "mistake_passport.state_transitioned", AnalyticsEventDomain.LEARNING_MASTERY, false, true, PROPS_LEARNING_OS_V7);
        register(specs, "twin_drill.issued", AnalyticsEventDomain.LEARNING_MASTERY, false, true, PROPS_LEARNING_OS_V7);
        register(specs, "twin_drill.completed", AnalyticsEventDomain.LEARNING_MASTERY, false, true, PROPS_LEARNING_OS_V7);
        register(specs, "retrieval_check.scheduled", AnalyticsEventDomain.LEARNING_MASTERY, false, true, PROPS_LEARNING_OS_V7);
        register(specs, "retrieval_check.completed", AnalyticsEventDomain.LEARNING_MASTERY, false, true, PROPS_LEARNING_OS_V7);
        register(specs, "daily_coach.plan_generated", AnalyticsEventDomain.LEARNING_MASTERY, false, true, PROPS_LEARNING_OS_V7);
        register(specs, "daily_coach.item_completed", AnalyticsEventDomain.LEARNING_MASTERY, false, true, PROPS_LEARNING_OS_V7);
        register(specs, "daily_coach.plan_rebalanced", AnalyticsEventDomain.LEARNING_MASTERY, false, true, PROPS_LEARNING_OS_V7);

        // EXAM (4 events)
        register(specs, "exam_target_configured", AnalyticsEventDomain.EXAM, false, true, PROPS_EXAM);
        register(specs, "exam_mock_started", AnalyticsEventDomain.EXAM, false, true, PROPS_EXAM);
        register(specs, "exam_mock_submitted", AnalyticsEventDomain.EXAM, false, true, PROPS_EXAM);
        register(specs, "exam_readiness_viewed", AnalyticsEventDomain.EXAM, true, false, PROPS_EXAM);

        // BILLING_MONETIZATION (9 events)
        register(specs, "paywall_viewed", AnalyticsEventDomain.BILLING_MONETIZATION, true, true, PROPS_BILLING_CLIENT_OR_SERVER);
        register(specs, "purchase_started", AnalyticsEventDomain.BILLING_MONETIZATION, true, true, PROPS_BILLING_CLIENT_OR_SERVER);
        register(specs, "purchase_failed", AnalyticsEventDomain.BILLING_MONETIZATION, true, true, PROPS_BILLING_CLIENT_OR_SERVER);
        register(specs, "restore_completed", AnalyticsEventDomain.BILLING_MONETIZATION, true, true, PROPS_BILLING_CLIENT_OR_SERVER);
        register(specs, "paywall.mistake_moment_shown", AnalyticsEventDomain.BILLING_MONETIZATION, true, true, PROPS_BILLING_CLIENT_OR_SERVER);
        register(specs, "paywall.twin_success_shown", AnalyticsEventDomain.BILLING_MONETIZATION, true, true, PROPS_BILLING_CLIENT_OR_SERVER);
        register(specs, "purchase_completed", AnalyticsEventDomain.BILLING_MONETIZATION, false, true, PROPS_BILLING_SERVER_ONLY);
        register(specs, "entitlement_changed", AnalyticsEventDomain.BILLING_MONETIZATION, false, true, PROPS_BILLING_SERVER_ONLY);
        register(specs, "paywall.trial_started", AnalyticsEventDomain.BILLING_MONETIZATION, false, true, PROPS_BILLING_SERVER_ONLY);

        // LIFECYCLE_RETENTION (2 events)
        register(specs, "lifecycle_notification_opened", AnalyticsEventDomain.LIFECYCLE_RETENTION, true, false, PROPS_LIFECYCLE);
        register(specs, "weekly_digest_viewed", AnalyticsEventDomain.LIFECYCLE_RETENTION, true, false, PROPS_LIFECYCLE);

        REGISTRY = Collections.unmodifiableMap(specs);
        ORDERED_SPECS = List.copyOf(specs.values());
    }

    private AnalyticsEventCatalog() {
    }

    private static void register(
        Map<String, AnalyticsEventSpec> target,
        String eventName,
        AnalyticsEventDomain domain,
        boolean clientAllowed,
        boolean serverAllowed,
        Set<String> allowedPropertyKeys
    ) {
        AnalyticsEventSpec spec = new AnalyticsEventSpec(eventName, domain, clientAllowed, serverAllowed, allowedPropertyKeys);
        if (target.put(eventName, spec) != null) {
            throw new IllegalStateException("Duplicate analytics event registration: " + eventName);
        }
    }

    public static Optional<AnalyticsEventSpec> find(String eventName) {
        if (eventName == null || eventName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(REGISTRY.get(eventName.trim()));
    }

    public static AnalyticsEventSpec requireEvent(String eventName) {
        return find(eventName).orElseThrow(() -> new ApiProblemException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.ANALYTICS_EVENT_UNKNOWN,
            "Analytics event name is not in the canonical closed catalog",
            false,
            "USE_CANONICAL_EVENT"
        ));
    }

    public static void requireSourceAllowed(AnalyticsEventSpec spec, AnalyticsEventSource source) {
        if (!spec.allowsSource(source)) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.ANALYTICS_EVENT_VALIDATION_FAILED,
                "Analytics event '" + spec.eventName() + "' is not permitted from source " + source.name(),
                false,
                "DROP_INVALID_EVENT"
            );
        }
    }

    public static List<AnalyticsEventSpec> allSpecs() {
        return ORDERED_SPECS;
    }
}
