package com.verifiedai.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class PromptRegistryValidatorTest {

    @Test
    void resolvesDefaultExecutablePromptAndSchemaContracts() {
        PromptRegistryValidator validator =
            validator(
                PromptRegistry.defaults(),
                OutputSchemaRegistry.defaults(),
                true,
                true
            );

        assertThatCode(() ->
            validator.validate(
                plan(AiCapability.VISION_PARSE),
                "local"
            )
        ).doesNotThrowAnyException();

        assertThatCode(() ->
            validator.validate(
                plan(AiCapability.PROBLEM_NORMALIZE),
                "test"
            )
        ).doesNotThrowAnyException();

        assertThatCode(() ->
            validator.validate(
                plan(AiCapability.PROBLEM_CLASSIFY),
                "local"
            )
        ).doesNotThrowAnyException();
    }

    @Test
    void executableCapabilityDescriptorsHaveRegistryCoverage() {
        PromptRegistry registry =
            PromptRegistry.defaults();

        for (
            AiCapability capability :
            List.of(
                AiCapability.VISION_PARSE,
                AiCapability.PROBLEM_NORMALIZE,
                AiCapability.PROBLEM_CLASSIFY
            )
        ) {
            AiCapabilityDescriptor descriptor =
                AiCapabilityRegistry
                    .defaults()
                    .require(capability);

            PromptRegistryEntry entry = registry.require(descriptor.defaultPromptId(), "v001");
            assertThat(entry.capability()).isEqualTo(capability);
            assertThat(entry.outputSchemaVersion()).isEqualTo(descriptor.defaultSchemaVersion());
            assertThat(entry.compatibleRoutePolicyVersion()).isEqualTo(descriptor.defaultRoutePolicyVersion());
        }
    }

    @Test
    void missingPromptRegistryEntryFailsClosed() {
        PromptRegistryValidator validator =
            validator(
                new PromptRegistry(List.of()),
                OutputSchemaRegistry.defaults(),
                true,
                true
            );

        assertFailure(
            () -> validator.validate(
                plan(AiCapability.VISION_PARSE),
                "local"
            ),
            PromptRegistryFailureCode
                .AI_PROMPT_REGISTRY_ENTRY_MISSING
        );
    }

    @Test
    void missingPromptArtifactFailsClosed() {
        PromptRegistryValidator validator =
            validator(
                PromptRegistry.defaults(),
                OutputSchemaRegistry.defaults(),
                false,
                true
            );

        assertFailure(
            () -> validator.validate(
                plan(AiCapability.VISION_PARSE),
                "local"
            ),
            PromptRegistryFailureCode
                .AI_PROMPT_ARTIFACT_MISSING
        );
    }

    @Test
    void missingSchemaArtifactFailsClosed() {
        PromptRegistryValidator validator =
            validator(
                PromptRegistry.defaults(),
                OutputSchemaRegistry.defaults(),
                true,
                false
            );

        assertFailure(
            () -> validator.validate(
                plan(AiCapability.PROBLEM_CLASSIFY),
                "local"
            ),
            PromptRegistryFailureCode
                .AI_SCHEMA_ARTIFACT_MISSING
        );
    }

    @Test
    void missingLogicalSchemaRegistryEntryFailsClosed() {
        PromptRegistryValidator validator =
            validator(
                PromptRegistry.defaults(),
                new OutputSchemaRegistry(List.of()),
                true,
                true
            );

        assertFailure(
            () -> validator.validate(
                plan(AiCapability.PROBLEM_NORMALIZE),
                "local"
            ),
            PromptRegistryFailureCode
                .AI_SCHEMA_ARTIFACT_MISSING
        );
    }

    @Test
    void capabilityMismatchFailsClosed() {
        PromptRegistryEntry wrongCapability =
            entry(
                "vision-recognition",
                "v001",
                AiCapability.PROBLEM_CLASSIFY,
                "recognition-evidence-v1",
                "vision-route-v1",
                PromptReleaseStage.LOCAL_ONLY
            );

        PromptRegistryValidator validator =
            validator(
                new PromptRegistry(List.of(wrongCapability)),
                OutputSchemaRegistry.defaults(),
                true,
                true
            );

        assertFailure(
            () -> validator.validate(
                plan(AiCapability.VISION_PARSE),
                "local"
            ),
            PromptRegistryFailureCode
                .AI_PROMPT_CAPABILITY_MISMATCH
        );
    }

    @Test
    void schemaVersionMismatchFailsClosed() {
        PromptRegistryValidator validator =
            validator(
                PromptRegistry.defaults(),
                OutputSchemaRegistry.defaults(),
                true,
                true
            );

        assertFailure(
            () -> validator.validate(
                plan(
                    AiCapability.PROBLEM_CLASSIFY,
                    "problem-classifier",
                    "v001",
                    "problem-parse-v1",
                    "problem-classifier-route-v1",
                    AiRoutePlan.ReleaseStage.LOCAL_ONLY
                ),
                "local"
            ),
            PromptRegistryFailureCode
                .AI_PROMPT_SCHEMA_VERSION_MISMATCH
        );
    }

    @Test
    void routePolicyIncompatibilityFailsClosed() {
        PromptRegistryValidator validator =
            validator(
                PromptRegistry.defaults(),
                OutputSchemaRegistry.defaults(),
                true,
                true
            );

        assertFailure(
            () -> validator.validate(
                plan(
                    AiCapability.PROBLEM_CLASSIFY,
                    "problem-classifier",
                    "v001",
                    "problem-classification-v1",
                    "wrong-route-v1",
                    AiRoutePlan.ReleaseStage.LOCAL_ONLY
                ),
                "local"
            ),
            PromptRegistryFailureCode
                .AI_PROMPT_ROUTE_POLICY_INCOMPATIBLE
        );
    }

    @Test
    void productionRejectsLocalOnlyPrompt() {
        PromptRegistryValidator validator =
            validator(
                PromptRegistry.defaults(),
                OutputSchemaRegistry.defaults(),
                true,
                true
            );

        assertFailure(
            () -> validator.validate(
                plan(
                    AiCapability.PROBLEM_CLASSIFY,
                    "problem-classifier",
                    "v001",
                    "problem-classification-v1",
                    "problem-classifier-route-v1",
                    AiRoutePlan.ReleaseStage.PRODUCTION
                ),
                "production"
            ),
            PromptRegistryFailureCode
                .AI_PROMPT_NOT_PRODUCTION_APPROVED
        );
    }

    @Test
    void productionRejectsStagingPrompt() {
        PromptRegistryEntry stagingPrompt =
            entry(
                "problem-classifier",
                "v001",
                AiCapability.PROBLEM_CLASSIFY,
                "problem-classification-v1",
                "problem-classifier-route-v1",
                PromptReleaseStage.STAGING
            );

        PromptRegistryValidator validator =
            validator(
                new PromptRegistry(List.of(stagingPrompt)),
                OutputSchemaRegistry.defaults(),
                true,
                true
            );

        assertFailure(
            () -> validator.validate(
                plan(
                    AiCapability.PROBLEM_CLASSIFY,
                    "problem-classifier",
                    "v001",
                    "problem-classification-v1",
                    "problem-classifier-route-v1",
                    AiRoutePlan.ReleaseStage.PRODUCTION
                ),
                "production"
            ),
            PromptRegistryFailureCode
                .AI_PROMPT_NOT_PRODUCTION_APPROVED
        );
    }

    @Test
    void productionAcceptsProductionPrompt() {
        PromptRegistryEntry productionPrompt =
            entry(
                "problem-classifier",
                "v001",
                AiCapability.PROBLEM_CLASSIFY,
                "problem-classification-v1",
                "problem-classifier-route-v1",
                PromptReleaseStage.PRODUCTION
            );

        PromptRegistryValidator validator =
            validator(
                new PromptRegistry(List.of(productionPrompt)),
                OutputSchemaRegistry.defaults(),
                true,
                true
            );

        assertThatCode(() ->
            validator.validate(
                plan(
                    AiCapability.PROBLEM_CLASSIFY,
                    "problem-classifier",
                    "v001",
                    "problem-classification-v1",
                    "problem-classifier-route-v1",
                    AiRoutePlan.ReleaseStage.PRODUCTION
                ),
                "production"
            )
        ).doesNotThrowAnyException();
    }

    @Test
    void disabledFutureCapabilityIsNotValidatedAsExecutable() {
        CountingPromptResolver promptResolver =
            new CountingPromptResolver(true);

        PromptRegistryValidator validator =
            new PromptRegistryValidator(
                PromptRegistry.defaults(),
                OutputSchemaRegistry.defaults(),
                promptResolver,
                new CountingSchemaResolver(true)
            );

        EnumMap<AiCapability, AiRoutePolicy> policies =
            new EnumMap<>(AiCapability.class);

        policies.put(
            AiCapability.SOLVE,
            AiRoutePolicy.disabled(AiCapability.SOLVE)
        );

        assertThatCode(() ->
            validator.validateEnabledRoutes(
                policies.values(),
                "local"
            )
        ).doesNotThrowAnyException();

        assertThat(promptResolver.loadCount).isZero();
    }

    @Test
    void duplicatePromptVersionFailsRegistryConstruction() {
        PromptRegistryEntry first =
            entry(
                "problem-classifier",
                "v001",
                AiCapability.PROBLEM_CLASSIFY,
                "problem-classification-v1",
                "problem-classifier-route-v1",
                PromptReleaseStage.LOCAL_ONLY
            );

        PromptRegistryEntry duplicate =
            entry(
                "problem-classifier",
                "v001",
                AiCapability.PROBLEM_CLASSIFY,
                "problem-classification-v1",
                "problem-classifier-route-v1",
                PromptReleaseStage.PRODUCTION
            );

        assertFailure(
            () -> new PromptRegistry(
                List.of(first, duplicate)
            ),
            PromptRegistryFailureCode
                .AI_PROMPT_REGISTRY_DUPLICATE
        );
    }

    @Test
    void duplicateLogicalSchemaVersionFailsRegistryConstruction() {
        assertFailure(
            () -> new OutputSchemaRegistry(
                List.of(
                    new OutputSchemaRegistryEntry(
                        "problem-classification-v1",
                        "schemas/problem-classification.schema.json",
                        "ai-platform",
                        "urn:verified-ai:problem-classification:v1",
                        OutputSemanticContract.PROBLEM_CLASSIFICATION,
                        "a".repeat(64)
                    ),
                    new OutputSchemaRegistryEntry(
                        "problem-classification-v1",
                        "schemas/other.schema.json",
                        "ai-platform",
                        "urn:verified-ai:problem-classification:v1",
                        OutputSemanticContract.PROBLEM_CLASSIFICATION,
                        "a".repeat(64)
                    )
                )
            ),
            PromptRegistryFailureCode
                .AI_SCHEMA_REGISTRY_DUPLICATE
        );
    }

    @Test
    void failureMessagesExposeOnlySafeIdentifiers() {
        PromptRegistryValidator validator =
            validator(
                PromptRegistry.defaults(),
                OutputSchemaRegistry.defaults(),
                false,
                true
            );

        assertThatThrownBy(() ->
            validator.validate(
                plan(AiCapability.VISION_PARSE),
                "local"
            )
        )
            .isInstanceOf(
                PromptRegistryValidationException.class
            )
            .hasMessageContaining(
                "vision-recognition"
            )
            .hasMessageContaining(
                "v001"
            )
            .hasMessageContaining(
                "recognition-evidence-v1"
            )
            .hasMessageNotContaining(
                "You extract raw visible recognition evidence"
            )
            .hasMessageNotContaining(
                "student@example.com"
            )
            .hasMessageNotContaining(
                "secret"
            );
    }

    private static PromptRegistryValidator validator(
        PromptRegistry promptRegistry,
        OutputSchemaRegistry schemaRegistry,
        boolean promptExists,
        boolean schemaExists
    ) {
        return new PromptRegistryValidator(
            promptRegistry,
            schemaRegistry,
            new CountingPromptResolver(promptExists),
            new CountingSchemaResolver(schemaExists)
        );
    }

    private static AiRoutePlan plan(
        AiCapability capability
    ) {
        return plan(
            capability,
            promptId(capability),
            "v001",
            schemaVersion(capability),
            routePolicyVersion(capability),
            AiRoutePlan.ReleaseStage.LOCAL_ONLY
        );
    }

    private static AiRoutePlan plan(
        AiCapability capability,
        String promptId,
        String promptVersion,
        String schemaVersion,
        String routePolicyVersion,
        AiRoutePlan.ReleaseStage releaseStage
    ) {
        return new AiRoutePlan(
            capability,
            routePolicyVersion,
            capability.name().toLowerCase(Locale.ROOT) + "-default-v1",
            new AiRouteTarget(releaseStage == AiRoutePlan.ReleaseStage.LOCAL_ONLY
                ? "LOCAL_FIXTURE" : "TEST_PROVIDER", "model-v1"),
            List.of(),
            promptId,
            promptVersion,
            schemaVersion,
            Duration.ofSeconds(10),
            1,
            16_384,
            10_000,
            "pricing-v1",
            AiRoutePlan.CachePolicy.DISABLED,
            AiRoutePlan.StreamingPolicy.DISABLED,
            "NO_TRAINING_BY_DEFAULT",
            "UNSPECIFIED",
            capability.name() + ":OPENAI",
            null,
            releaseStage
        );
    }

    private static PromptRegistryEntry entry(
        String promptId,
        String promptVersion,
        AiCapability capability,
        String schemaVersion,
        String routePolicyVersion,
        PromptReleaseStage releaseStage
    ) {
        return new PromptRegistryEntry(
            promptId,
            promptVersion,
            capability,
            "ai-platform",
            schemaVersion,
            "prompts/" + promptId + "/" + promptVersion + ".md",
            releaseStage,
            "evaluations/baselines/production-ingestion-v1.json",
            "evaluations/reports/test-release.json",
            List.of("safe note"),
            routePolicyVersion,
            PromptTrainingEligibility.NOT_ELIGIBLE,
            "a".repeat(64),
            releaseStage == PromptReleaseStage.PRODUCTION || releaseStage == PromptReleaseStage.STAGING
                ? new PromptReleaseApproval(
                    "a".repeat(64),
                    OutputSchemaRegistry.defaults().require(schemaVersion).contentSha256(),
                    "evaluations/baselines/production-ingestion-v1.json",
                    "evaluations/reports/test-release.json",
                    "docs/approvals/test-release.md",
                    "test-reviewer",
                    java.time.Instant.parse("2026-01-01T00:00:00Z")
                ) : null
        );
    }

    private static String routePolicyVersion(
        AiCapability capability
    ) {
        return switch (capability) {
            case VISION_PARSE -> "vision-route-v1";
            case PROBLEM_NORMALIZE -> "problem-parser-route-v1";
            case PROBLEM_CLASSIFY -> "problem-classifier-route-v1";
            default -> capability.name().toLowerCase(Locale.ROOT) + "-route-v1";
        };
    }

    private static String promptId(
        AiCapability capability
    ) {
        return switch (capability) {
            case VISION_PARSE -> "vision-recognition";
            case PROBLEM_NORMALIZE -> "problem-parser";
            case PROBLEM_CLASSIFY -> "problem-classifier";
            default -> capability.name().toLowerCase(Locale.ROOT);
        };
    }

    private static String schemaVersion(
        AiCapability capability
    ) {
        return switch (capability) {
            case VISION_PARSE -> "recognition-evidence-v1";
            case PROBLEM_NORMALIZE -> "problem-parse-v1";
            case PROBLEM_CLASSIFY -> "problem-classification-v1";
            default -> capability.name().toLowerCase(Locale.ROOT) + "-v1";
        };
    }

    private static void assertFailure(
        ThrowingRunnable runnable,
        PromptRegistryFailureCode code
    ) {
        assertThatThrownBy(runnable::run)
            .isInstanceOf(
                PromptRegistryValidationException.class
            )
            .extracting(
                exception ->
                    ((PromptRegistryValidationException)
                        exception).code()
            )
            .isEqualTo(code);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }

    private static final class CountingPromptResolver
        implements PromptArtifactResolver {

        private final boolean exists;
        private int loadCount;

        private CountingPromptResolver(
            boolean exists
        ) {
            this.exists = exists;
        }

        @Override
        public PromptArtifact load(
            PromptRegistryEntry entry
        ) {
            loadCount++;

            if (!exists) {
                throw new PromptRegistryValidationException(
                    PromptRegistryFailureCode
                        .AI_PROMPT_ARTIFACT_MISSING,
                    "missing prompt artifact: capability="
                        + entry.capability()
                        + " promptId="
                        + entry.promptId()
                        + " promptVersion="
                        + entry.promptVersion()
                        + " schemaVersion="
                        + entry.outputSchemaVersion()
                        + " routePolicyVersion="
                        + entry.compatibleRoutePolicyVersion()
                );
            }

            return new PromptArtifact(
                entry.promptArtifactPath(),
                1
            );
        }
    }

    private static final class CountingSchemaResolver
        implements OutputSchemaArtifactResolver {

        private final boolean exists;

        private CountingSchemaResolver(
            boolean exists
        ) {
            this.exists = exists;
        }

        @Override
        public OutputSchemaArtifact load(
            OutputSchemaRegistryEntry schema,
            PromptRegistryEntry prompt
        ) {
            if (!exists) {
                throw new PromptRegistryValidationException(
                    PromptRegistryFailureCode
                        .AI_SCHEMA_ARTIFACT_MISSING,
                    "missing schema artifact: schemaVersion="
                        + schema.schemaVersion()
                );
            }

            return new OutputSchemaArtifact(
                schema.artifactPath(),
                1
            );
        }
    }
}
