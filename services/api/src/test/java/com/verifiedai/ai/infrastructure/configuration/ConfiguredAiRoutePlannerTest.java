package com.verifiedai.ai.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.ai.application.AiCapability;
import com.verifiedai.ai.application.AiCapabilityRegistry;
import com.verifiedai.ai.application.AiRoutePlan;
import com.verifiedai.ai.application.OutputSchemaArtifact;
import com.verifiedai.ai.application.OutputSchemaRegistry;
import com.verifiedai.ai.application.PromptArtifact;
import com.verifiedai.ai.application.PromptRegistry;
import com.verifiedai.ai.application.PromptRegistryFailureCode;
import com.verifiedai.ai.application.PromptRegistryValidationException;
import com.verifiedai.ai.application.PromptRegistryValidator;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ConfiguredAiRoutePlannerTest {

    @Test
    void productionCannotEnableLocalFixtureProvider() {
        assertThatThrownBy(() ->
            planner(
                vision(
                    true,
                    "LOCAL_FIXTURE",
                    AiRoutePlan.ReleaseStage.PRODUCTION
                ),
                disabledParser(),
                disabledClassifier(),
                registry(true, Map.of()),
                "production"
            )
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessageContaining(
                "LOCAL_FIXTURE"
            )
            .hasMessageContaining(
                "VISION_PARSE"
            );
    }

    @Test
    void productionCannotDisableUsageLedgerForMaterialCapability() {
        assertThatThrownBy(() ->
            planner(
                vision(
                    true,
                    "OPENAI",
                    AiRoutePlan.ReleaseStage.PRODUCTION
                ),
                disabledParser(),
                disabledClassifier(),
                registry(false, Map.of()),
                "production"
            )
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessageContaining(
                "requires usage ledger"
            )
            .hasMessageContaining(
                "VISION_PARSE"
            );
    }

    @Test
    void productionEnabledRouteWithLocalOnlyPromptFailsStartup() {
        assertThatThrownBy(() ->
            planner(
                vision(
                    true,
                    "OPENAI",
                    AiRoutePlan.ReleaseStage.PRODUCTION
                ),
                disabledParser(),
                disabledClassifier(),
                registry(true, Map.of()),
                "production"
            )
        )
            .isInstanceOf(
                PromptRegistryValidationException.class
            )
            .extracting(
                exception ->
                    ((PromptRegistryValidationException)
                        exception).code()
            )
            .isEqualTo(
                PromptRegistryFailureCode
                    .AI_PROMPT_NOT_PRODUCTION_APPROVED
            );
    }

    @Test
    void unapprovedSolveCapabilityCannotBeEnabled() {
        assertThatThrownBy(() ->
            planner(
                disabledVision(),
                disabledParser(),
                disabledClassifier(),
                registry(
                    true,
                    Map.of(
                        AiCapability.SOLVE,
                        futureCapability(true)
                    )
                ),
                "local"
            )
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessageContaining(
                "SOLVE"
            )
            .hasMessageContaining(
                "SOLVE activation blocked"
            );
    }

    @Test
    void futureArbitrateAndTutorCapabilitiesCannotBeEnabled() {
        for (
            AiCapability capability :
            List.of(
                AiCapability.ARBITRATE,
                AiCapability.TUTOR
            )
        ) {
            assertThatThrownBy(() ->
                planner(
                    disabledVision(),
                    disabledParser(),
                    disabledClassifier(),
                    registry(
                        true,
                        Map.of(
                            capability,
                            futureCapability(true)
                        )
                    ),
                    "local"
                )
            )
                .isInstanceOf(
                    IllegalStateException.class
                )
                .hasMessageContaining(
                    capability.name()
                )
                .hasMessageContaining(
                    "cannot be enabled"
                );
        }
    }

    @Test
    void prodAliasCannotBypassFixtureGuard() {
        assertThatThrownBy(() -> planner(
            vision(true, "LOCAL_FIXTURE", AiRoutePlan.ReleaseStage.PRODUCTION),
            disabledParser(), disabledClassifier(), registry(true, Map.of()), " prod "))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("LOCAL_FIXTURE");
    }

    @Test
    void stagingCannotEnableUnapprovedLocalArtifact() {
        assertThatThrownBy(() -> planner(
            vision(true, "TEST_PROVIDER", AiRoutePlan.ReleaseStage.STAGING),
            disabledParser(), disabledClassifier(), registry(true, Map.of()), "staging"))
            .isInstanceOf(PromptRegistryValidationException.class)
            .extracting(e -> ((PromptRegistryValidationException) e).code())
            .isEqualTo(PromptRegistryFailureCode.AI_PROMPT_RELEASE_STAGE_INVALID);
    }

    @Test
    void localFixturesWorkWithRealClasspathValidation() {
        org.assertj.core.api.Assertions.assertThatCode(() -> new ConfiguredAiRoutePlanner(
            vision(true, "LOCAL_FIXTURE", AiRoutePlan.ReleaseStage.LOCAL_ONLY),
            disabledParser(), disabledClassifier(), registry(true, Map.of()),
            AiCapabilityRegistry.defaults(),
            new PromptRegistryValidator(PromptRegistry.defaults(), OutputSchemaRegistry.defaults(),
                new ClasspathPromptArtifactResolver(new org.springframework.core.io.DefaultResourceLoader()),
                new ClasspathOutputSchemaArtifactResolver(new org.springframework.core.io.DefaultResourceLoader())),
            "local")).doesNotThrowAnyException();
    }

    private static ConfiguredAiRoutePlanner planner(
        AiVisionRecognitionProperties vision,
        AiProblemParserProperties parser,
        AiProblemClassifierProperties classifier,
        AiRouteRegistryProperties registry,
        String environment
    ) {
        return new ConfiguredAiRoutePlanner(
            vision,
            parser,
            classifier,
            registry,
            AiCapabilityRegistry.defaults(),
            promptRegistryValidator(),
            environment
        );
    }

    private static PromptRegistryValidator
    promptRegistryValidator() {
        return new PromptRegistryValidator(
            PromptRegistry.defaults(),
            OutputSchemaRegistry.defaults(),
            entry -> new PromptArtifact(
                entry.promptArtifactPath(),
                1
            ),
            (schema, prompt) -> new OutputSchemaArtifact(
                schema.artifactPath(),
                1
            )
        );
    }

    private static AiRouteRegistryProperties registry(
        boolean usageLedgerEnabled,
        Map<
            AiCapability,
            AiRouteRegistryProperties.CapabilityRoute
            > capabilities
    ) {
        return new AiRouteRegistryProperties(
            new AiRouteRegistryProperties.UsageLedger(
                usageLedgerEnabled
            ),
            capabilities
        );
    }

    private static AiRouteRegistryProperties.CapabilityRoute
    futureCapability(
        boolean enabled
    ) {
        return new AiRouteRegistryProperties.CapabilityRoute(
            enabled,
            null,
            null,
            null,
            List.of(),
            null,
            null,
            null,
            null,
            0,
            0,
            0,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            AiRoutePlan.ReleaseStage.DISABLED
        );
    }

    private static AiVisionRecognitionProperties disabledVision() {
        return vision(
            false,
            "LOCAL_FIXTURE",
            AiRoutePlan.ReleaseStage.DISABLED
        );
    }

    private static AiVisionRecognitionProperties vision(
        boolean enabled,
        String primaryProvider,
        AiRoutePlan.ReleaseStage releaseStage
    ) {
        return new AiVisionRecognitionProperties(
            enabled,
            primaryProvider,
            "local-fixture-vision-v1",
            "",
            "",
            "vision-route-v1",
            "vision-parse-default-v1",
            "vision-recognition",
            "v001",
            "recognition-evidence-v1",
            Duration.ofSeconds(20),
            2,
            65_536,
            20_000,
            "vision-pricing-v1",
            releaseStage
        );
    }

    private static AiProblemParserProperties disabledParser() {
        return new AiProblemParserProperties(
            false,
            "LOCAL_FIXTURE",
            "local-fixture-problem-parser-v1",
            "",
            "",
            "problem-parser-route-v1",
            "problem-normalize-default-v1",
            "problem-parser",
            "v001",
            "problem-parse-v1",
            Duration.ofSeconds(20),
            2,
            65_536,
            20_000,
            "parser-pricing-v1",
            AiRoutePlan.ReleaseStage.DISABLED
        );
    }

    private static AiProblemClassifierProperties
    disabledClassifier() {
        return new AiProblemClassifierProperties(
            false,
            "LOCAL_FIXTURE",
            "local-fixture-problem-classifier-v1",
            "",
            "",
            "problem-classifier-route-v1",
            "problem-classify-default-v1",
            "problem-classifier",
            "v001",
            "problem-classification-v1",
            Duration.ofSeconds(15),
            2,
            16_384,
            10_000,
            "classifier-pricing-v1",
            AiRoutePlan.ReleaseStage.DISABLED
        );
    }
}
