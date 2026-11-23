package com.verifiedai.featureflags.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.featureflags.domain.FeatureFlagEvaluationPolicy.EvaluatedFlag;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

final class FeatureFlagEvaluationPolicyTest {

    private final Instant now = Instant.parse("2026-11-23T12:00:00Z");
    private final UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000123");

    @Test
    void killSwitchSupremacyAlwaysOverridesEnabledRolloutAndCurrentValue() {
        FeatureFlagDefinitionSnapshot booleanKilled = new FeatureFlagDefinitionSnapshot(
            "pdf_upload_enabled",
            FlagCategory.KILL_SWITCH,
            FlagValueType.BOOLEAN,
            true,
            true,
            true,
            10_000,
            "v1",
            "true",
            "true",
            "platform-security",
            "Operational kill switch",
            null,
            1L,
            false,
            now,
            now
        );

        EvaluatedFlag evalBool = FeatureFlagEvaluationPolicy.evaluateFlag(booleanKilled, userId, now);
        assertThat(evalBool.enabled()).isFalse();
        assertThat(evalBool.killSwitchActive()).isTrue();
        assertThat(evalBool.valueJson()).isEqualTo("false");
        assertThat(evalBool.reason()).isEqualTo("KILL_SWITCH_ACTIVE");

        FeatureFlagDefinitionSnapshot intKilled = new FeatureFlagDefinitionSnapshot(
            "strong_model_pct",
            FlagCategory.REMOTE_CONFIG,
            FlagValueType.INTEGER,
            false,
            true,
            true,
            10_000,
            "v1",
            "0",
            "85",
            "platform-ai",
            "Strong model routing ceiling",
            null,
            2L,
            false,
            now,
            now
        );

        EvaluatedFlag evalInt = FeatureFlagEvaluationPolicy.evaluateFlag(intKilled, userId, now);
        assertThat(evalInt.enabled()).isFalse();
        assertThat(evalInt.killSwitchActive()).isTrue();
        assertThat(evalInt.valueJson()).isEqualTo("0");
        assertThat(evalInt.reason()).isEqualTo("KILL_SWITCH_ACTIVE");
    }

    @Test
    void experimentStateMachineAllowsLegalTransitionsAndBlocksIllegalOnes() {
        // Legal transitions
        ExperimentStateMachine.validateTransition(ExperimentStatus.DRAFT, ExperimentStatus.RUNNING);
        ExperimentStateMachine.validateTransition(ExperimentStatus.DRAFT, ExperimentStatus.KILLED);
        ExperimentStateMachine.validateTransition(ExperimentStatus.RUNNING, ExperimentStatus.PAUSED);
        ExperimentStateMachine.validateTransition(ExperimentStatus.RUNNING, ExperimentStatus.KILLED);
        ExperimentStateMachine.validateTransition(ExperimentStatus.RUNNING, ExperimentStatus.CONCLUDED);
        ExperimentStateMachine.validateTransition(ExperimentStatus.PAUSED, ExperimentStatus.RUNNING);
        ExperimentStateMachine.validateTransition(ExperimentStatus.PAUSED, ExperimentStatus.KILLED);
        ExperimentStateMachine.validateTransition(ExperimentStatus.PAUSED, ExperimentStatus.CONCLUDED);
        ExperimentStateMachine.validateTransition(ExperimentStatus.KILLED, ExperimentStatus.PAUSED);
        ExperimentStateMachine.validateTransition(ExperimentStatus.KILLED, ExperimentStatus.CONCLUDED);

        // KILLED -> RUNNING directly is prohibited (must pause/inspect first)
        assertThatThrownBy(() -> ExperimentStateMachine.validateTransition(ExperimentStatus.KILLED, ExperimentStatus.RUNNING))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> {
                ApiProblemException problem = (ApiProblemException) ex;
                assertThat(problem.status()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(problem.code()).isEqualTo(ApiErrorCode.FEATURE_FLAG_INVALID_TRANSITION);
            });

        // CONCLUDED is strictly terminal
        for (ExperimentStatus target : ExperimentStatus.values()) {
            assertThatThrownBy(() -> ExperimentStateMachine.validateTransition(ExperimentStatus.CONCLUDED, target))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                    .isEqualTo(ApiErrorCode.FEATURE_FLAG_INVALID_TRANSITION));
        }

        // Modifying frozen/terminal state directly without valid transition throws CONFLICT
        assertThatThrownBy(() -> ExperimentStateMachine.validateUpdate(ExperimentStatus.KILLED, ExperimentStatus.KILLED))
            .isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(() -> ExperimentStateMachine.validateUpdate(ExperimentStatus.CONCLUDED, ExperimentStatus.CONCLUDED))
            .isInstanceOf(ApiProblemException.class);
        ExperimentStateMachine.validateUpdate(ExperimentStatus.RUNNING, ExperimentStatus.RUNNING);
        ExperimentStateMachine.validateUpdate(ExperimentStatus.DRAFT, ExperimentStatus.DRAFT);
    }

    @Test
    void filterClientSafeFlagsStrictlyExcludesServerOnlyFlags() {
        List<FeatureFlagDefinitionSnapshot> all = List.of(
            FeatureFlagEvaluationPolicy.canonicalDefault("secondary_solver_enabled").orElseThrow(),
            FeatureFlagEvaluationPolicy.canonicalDefault("strong_model_pct").orElseThrow(),
            FeatureFlagEvaluationPolicy.canonicalDefault("proprietary_canary_pct").orElseThrow(),
            FeatureFlagEvaluationPolicy.canonicalDefault("daily_solve_quota_override").orElseThrow(),
            FeatureFlagEvaluationPolicy.canonicalDefault("pdf_upload_enabled").orElseThrow(),
            FeatureFlagEvaluationPolicy.canonicalDefault("client_platform_health_screen").orElseThrow(),
            FeatureFlagEvaluationPolicy.canonicalDefault("client_mistake_moment_paywall_ui").orElseThrow()
        );

        List<FeatureFlagDefinitionSnapshot> clientSafe = FeatureFlagEvaluationPolicy.filterClientSafeFlags(all, 64);
        assertThat(clientSafe)
            .extracting(FeatureFlagDefinitionSnapshot::flagKey)
            .containsExactly(
                "pdf_upload_enabled",
                "client_platform_health_screen",
                "client_mistake_moment_paywall_ui"
            )
            .doesNotContain(
                "secondary_solver_enabled",
                "strong_model_pct",
                "proprietary_canary_pct",
                "daily_solve_quota_override"
            );

        assertThat(FeatureFlagEvaluationPolicy.filterClientSafeFlags(all, 2)).hasSize(2);
    }

    @Test
    void valueValidationRejectsTypeAndBoundsMismatchesWith422() {
        assertThat(FeatureFlagEvaluationPolicy.validateAndNormalizeValue("pdf_upload_enabled", FlagValueType.BOOLEAN, " true "))
            .isEqualTo("true");
        assertThat(FeatureFlagEvaluationPolicy.validateAndNormalizeValue("strong_model_pct", FlagValueType.INTEGER, "25"))
            .isEqualTo("25");
        assertThat(FeatureFlagEvaluationPolicy.validateAndNormalizeValue("daily_solve_quota_override", FlagValueType.INTEGER, "5"))
            .isEqualTo("5");
        assertThat(FeatureFlagEvaluationPolicy.validateAndNormalizeValue("custom_weight", FlagValueType.DECIMAL, "0.75"))
            .isEqualTo("0.75");
        assertThat(FeatureFlagEvaluationPolicy.validateAndNormalizeValue("custom_mode", FlagValueType.STRING, "\"balanced\""))
            .isEqualTo("\"balanced\"");

        assertInvalidValue("pdf_upload_enabled", FlagValueType.BOOLEAN, "\"true\"");
        assertInvalidValue("pdf_upload_enabled", FlagValueType.BOOLEAN, "1");
        assertInvalidValue("strong_model_pct", FlagValueType.INTEGER, "-1");
        assertInvalidValue("strong_model_pct", FlagValueType.INTEGER, "101");
        assertInvalidValue("strong_model_pct", FlagValueType.INTEGER, "12.5");
        assertInvalidValue("daily_solve_quota_override", FlagValueType.INTEGER, "0");
        assertInvalidValue("daily_solve_quota_override", FlagValueType.INTEGER, "51");
        assertInvalidValue("custom_weight", FlagValueType.DECIMAL, "\"bad\"");
        assertInvalidValue("custom_mode", FlagValueType.STRING, "\"" + "x".repeat(129) + "\"");
        assertInvalidValue("custom_mode", FlagValueType.STRING, "\"" + "x".repeat(600) + "\"");
        assertInvalidValue("custom_mode", FlagValueType.BOOLEAN, "null");
    }

    @Test
    void canonicalDefaultsFailClosedForRiskyFlagsAndTrainingEligibleFalse() {
        List<String> keys = List.of(
            "secondary_solver_enabled",
            "advanced_verification_enabled",
            "tutor_v2_enabled",
            "adaptive_plan_v2_enabled",
            "pdf_upload_enabled",
            "verification_method_false_positive_kill_switch",
            "strong_model_pct",
            "proprietary_canary_pct",
            "daily_solve_quota_override",
            "client_platform_health_screen",
            "client_mistake_moment_paywall_ui"
        );
        for (String key : keys) {
            FeatureFlagDefinitionSnapshot def = FeatureFlagEvaluationPolicy.canonicalDefault(key).orElseThrow();
            assertThat(def.trainingEligible()).isFalse();
            assertThat(def.killSwitchActive()).isFalse();
            if ("pdf_upload_enabled".equals(key)) {
                assertThat(def.enabled()).isTrue();
            } else if ("strong_model_pct".equals(key)) {
                assertThat(def.currentValueJson()).isEqualTo("0");
            } else {
                assertThat(def.enabled()).isFalse();
            }
        }
    }

    private static void assertInvalidValue(String key, FlagValueType type, String raw) {
        assertThatThrownBy(() -> FeatureFlagEvaluationPolicy.validateAndNormalizeValue(key, type, raw))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> {
                ApiProblemException problem = (ApiProblemException) ex;
                assertThat(problem.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                assertThat(problem.code()).isEqualTo(ApiErrorCode.FEATURE_FLAG_VALIDATION_FAILED);
            });
    }
}
