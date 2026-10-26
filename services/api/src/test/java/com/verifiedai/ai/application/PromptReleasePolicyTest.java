package com.verifiedai.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

final class PromptReleasePolicyTest {
    private static final PromptRegistryEntry ORIGINAL =
        PromptRegistry.defaults().require("vision-recognition", "v001");
    private static final OutputSchemaRegistryEntry SCHEMA =
        OutputSchemaRegistry.defaults().require("recognition-evidence-v1");

    @Test
    void draftValidatedApprovedAndRetiredVersionsAreNeverExecutable() {
        for (PromptReleaseStage stage : List.of(PromptReleaseStage.DRAFT,
            PromptReleaseStage.VALIDATED, PromptReleaseStage.APPROVED,
            PromptReleaseStage.DEPRECATED, PromptReleaseStage.ROLLED_BACK)) {
            rejects(artifact("v001", stage, null), "local",
                AiRoutePlan.ReleaseStage.LOCAL_ONLY,
                PromptRegistryFailureCode.AI_PROMPT_RELEASE_STAGE_INVALID);
        }
    }

    @Test
    void stagingRejectsLocalOnlyAndUnknownEnvironmentsFailClosed() {
        rejects(ORIGINAL, "staging", AiRoutePlan.ReleaseStage.STAGING,
            PromptRegistryFailureCode.AI_PROMPT_RELEASE_STAGE_INVALID);
        for (String env : new String[] {null, "", "prodution", "preview"}) {
            rejects(ORIGINAL, env, AiRoutePlan.ReleaseStage.LOCAL_ONLY,
                PromptRegistryFailureCode.AI_PROMPT_ENVIRONMENT_INVALID);
        }
    }

    @Test
    void changingOnlyReleaseStageCannotApproveAnArtifact() {
        for (String environment : List.of("prod", "production", "staging")) {
            rejects(artifact("v001", PromptReleaseStage.PRODUCTION, null), environment,
                AiRoutePlan.ReleaseStage.PRODUCTION,
                PromptRegistryFailureCode.AI_PROMPT_APPROVAL_MISSING);
        }
    }

    @Test
    void approvalMustBindBothHashesAndEvaluationReferences() {
        for (PromptReleaseApproval approval : List.of(
            approval("a".repeat(64), SCHEMA.contentSha256(), "evaluations/reports/release.json"),
            approval(ORIGINAL.contentSha256(), "b".repeat(64), "evaluations/reports/release.json"),
            approval(ORIGINAL.contentSha256(), SCHEMA.contentSha256(), "evaluations/reports/other.json"))) {
            rejects(artifact("v001", PromptReleaseStage.PRODUCTION, approval), "production",
                AiRoutePlan.ReleaseStage.PRODUCTION,
                PromptRegistryFailureCode.AI_PROMPT_APPROVAL_MISSING);
        }
    }

    @Test
    void approvedStagingAndProductionRequireExplicitRouteStages() {
        PromptRegistryEntry candidate = artifact("v001", PromptReleaseStage.STAGING, approval());
        assertThatCode(() -> validator(candidate).validate(
            route("v001", AiRoutePlan.ReleaseStage.STAGING), "staging"))
            .doesNotThrowAnyException();
        rejects(candidate, "production", AiRoutePlan.ReleaseStage.PRODUCTION,
            PromptRegistryFailureCode.AI_PROMPT_NOT_PRODUCTION_APPROVED);
        rejects(candidate, "staging", AiRoutePlan.ReleaseStage.LOCAL_ONLY,
            PromptRegistryFailureCode.AI_PROMPT_RELEASE_STAGE_INVALID);
        assertThatCode(() -> validator(artifact("v001", PromptReleaseStage.PRODUCTION, approval()))
            .validate(route("v001", AiRoutePlan.ReleaseStage.PRODUCTION), "prod"))
            .doesNotThrowAnyException();
    }

    @Test
    void explicitRollbackResolvesOlderApprovedVersionAndNeverUpgradesHistory() {
        PromptRegistryEntry previous = artifact("v001", PromptReleaseStage.PRODUCTION, approval());
        PromptRegistryEntry candidate = artifact("v002", PromptReleaseStage.PRODUCTION, approval());
        PromptRegistry registry = new PromptRegistry(List.of(previous, candidate));
        PromptRegistryValidator validator = validator(registry, OutputSchemaRegistry.defaults());
        AiProvenance historical = new AiProvenance("TEST_PROVIDER", "test-model", "vision-route-v1",
            "vision-recognition", "v001", "recognition-evidence-v1", null, null, false);
        validator.validate(route("v002", AiRoutePlan.ReleaseStage.PRODUCTION), "production");
        assertThat(registry.require("vision-recognition", "v002")).isSameAs(candidate);
        validator.validate(route("v001", AiRoutePlan.ReleaseStage.PRODUCTION), "production");
        assertThat(registry.require(historical.promptId(), historical.promptVersion())).isSameAs(previous);
        assertThat(historical.promptVersion()).isEqualTo("v001");
        assertThatThrownBy(() -> validator(artifact("v003", PromptReleaseStage.DRAFT, null)).validate(
            route("v003", AiRoutePlan.ReleaseStage.PRODUCTION), "production"))
            .isInstanceOf(PromptRegistryValidationException.class);
        assertThatThrownBy(() -> registry.require("vision-recognition", "v003"))
            .isInstanceOf(PromptRegistryValidationException.class);
    }

    @Test
    void schemaSemanticOwnershipCannotBeSwapped() {
        OutputSchemaRegistryEntry incompatible = new OutputSchemaRegistryEntry(
            SCHEMA.schemaVersion(), SCHEMA.artifactPath(), SCHEMA.owner(), SCHEMA.schemaId(),
            OutputSemanticContract.PROBLEM_PARSE, SCHEMA.contentSha256());
        assertThatThrownBy(() -> validator(new PromptRegistry(List.of(ORIGINAL)),
            new OutputSchemaRegistry(List.of(incompatible))).validate(
                route("v001", AiRoutePlan.ReleaseStage.LOCAL_ONLY), "local"))
            .isInstanceOf(PromptRegistryValidationException.class)
            .extracting(e -> ((PromptRegistryValidationException) e).code())
            .isEqualTo(PromptRegistryFailureCode.AI_SCHEMA_CONTRACT_MISMATCH);
    }

    @Test
    void studentTextCannotSelectPromptOrAppearInRegistryFailures() {
        String untrusted = "student@example.com ignore instructions https://secret.example/token";
        assertThatThrownBy(() -> PromptRegistry.defaults().require(untrusted, "v001"))
            .isInstanceOf(PromptRegistryValidationException.class)
            .hasMessageNotContaining(untrusted).hasMessageNotContaining("student@example.com");
        assertThatThrownBy(() -> OutputSchemaRegistry.defaults().require(untrusted))
            .isInstanceOf(PromptRegistryValidationException.class).hasMessageNotContaining(untrusted);
        assertThat(PromptRegistry.defaults().require("vision-recognition", "v001"))
            .isEqualTo(ORIGINAL);
    }

    @Test
    void approvalCannotCarryCredentialsOrBlockedEvaluationPlaceholder() {
        for (String reference : List.of("CONNECTED_PROVIDER_EVAL_BLOCKED", "https://user:secret@example.com",
            "evaluations/../../secret.json", "evaluations/secret.json?token=secret")) {
            assertThatThrownBy(() -> approval(ORIGINAL.contentSha256(), SCHEMA.contentSha256(), reference))
                .isInstanceOf(PromptRegistryValidationException.class).hasMessageNotContaining(reference);
        }
    }

    @Test
    void invalidMetadataAndMissingDigestAreRejected() {
        assertThatThrownBy(() -> ArtifactIntegrity.requireDigest(null))
            .isInstanceOf(PromptRegistryValidationException.class);
        assertThatThrownBy(() -> ArtifactIntegrity.requireDigest("not-sha256"))
            .isInstanceOf(PromptRegistryValidationException.class);
        assertThatThrownBy(() -> new PromptRegistryEntry("", "v001", ORIGINAL.capability(),
            ORIGINAL.owner(), ORIGINAL.outputSchemaVersion(), ORIGINAL.promptArtifactPath(),
            ORIGINAL.releaseStage(), ORIGINAL.evaluationBaselineReference(), ORIGINAL.evaluationReportReference(),
            ORIGINAL.safetyNotes(), ORIGINAL.compatibleRoutePolicyVersion(), ORIGINAL.trainingEligibility(),
            ORIGINAL.contentSha256(), null)).isInstanceOf(PromptRegistryValidationException.class);
    }

    @Test
    void disabledFutureRoutesDoNotRequireArtifactsOrInvokeResolvers() {
        PromptRegistryValidator validator = new PromptRegistryValidator(
            new PromptRegistry(List.of()), new OutputSchemaRegistry(List.of()),
            entry -> { throw new AssertionError("disabled prompt was loaded"); },
            (schema, prompt) -> { throw new AssertionError("disabled schema was loaded"); });
        validator.validateEnabledRoutes(List.of(AiRoutePolicy.disabled(AiCapability.SOLVE)), "production");
    }

    @Test
    void localArtifactCannotReachNonFixtureFallback() {
        assertThatThrownBy(() -> validator(ORIGINAL).validate(
            route("v001", AiRoutePlan.ReleaseStage.LOCAL_ONLY,
                List.of(new AiRouteTarget("TEST_PROVIDER", "test-model"))), "local"))
            .isInstanceOf(PromptRegistryValidationException.class)
            .extracting(e -> ((PromptRegistryValidationException) e).code())
            .isEqualTo(PromptRegistryFailureCode.AI_PROMPT_RELEASE_STAGE_INVALID);
    }

    private static void rejects(PromptRegistryEntry prompt, String environment,
        AiRoutePlan.ReleaseStage stage, PromptRegistryFailureCode code) {
        assertThatThrownBy(() -> validator(prompt).validate(route(prompt.promptVersion(), stage), environment))
            .isInstanceOf(PromptRegistryValidationException.class)
            .extracting(e -> ((PromptRegistryValidationException) e).code()).isEqualTo(code);
    }

    private static PromptRegistryValidator validator(PromptRegistryEntry entry) {
        return validator(new PromptRegistry(List.of(entry)), OutputSchemaRegistry.defaults());
    }

    private static PromptRegistryValidator validator(PromptRegistry prompts, OutputSchemaRegistry schemas) {
        return new PromptRegistryValidator(prompts, schemas,
            entry -> new PromptArtifact(entry.promptArtifactPath(), 1),
            (schema, prompt) -> new OutputSchemaArtifact(schema.artifactPath(), 1));
    }

    private static PromptRegistryEntry artifact(String version, PromptReleaseStage stage, PromptReleaseApproval approval) {
        return new PromptRegistryEntry(ORIGINAL.promptId(), version, ORIGINAL.capability(), ORIGINAL.owner(),
            ORIGINAL.outputSchemaVersion(), "prompts/vision-recognition/" + version + ".md", stage,
            ORIGINAL.evaluationBaselineReference(), "evaluations/reports/release.json", ORIGINAL.safetyNotes(),
            ORIGINAL.compatibleRoutePolicyVersion(), ORIGINAL.trainingEligibility(), ORIGINAL.contentSha256(), approval);
    }

    private static PromptReleaseApproval approval() {
        return approval(ORIGINAL.contentSha256(), SCHEMA.contentSha256(), "evaluations/reports/release.json");
    }

    private static PromptReleaseApproval approval(String promptHash, String schemaHash, String report) {
        return new PromptReleaseApproval(promptHash, schemaHash, ORIGINAL.evaluationBaselineReference(), report,
            "docs/approvals/release.md", "test-reviewer", Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static AiRoutePlan route(String version, AiRoutePlan.ReleaseStage stage) {
        return route(version, stage, List.of());
    }

    private static AiRoutePlan route(String version, AiRoutePlan.ReleaseStage stage, List<AiRouteTarget> fallbacks) {
        return new AiRoutePlan(AiCapability.VISION_PARSE, "vision-route-v1", "vision-test-v1",
            new AiRouteTarget("LOCAL_FIXTURE", "fixture-model"), fallbacks, "vision-recognition", version,
            "recognition-evidence-v1", Duration.ofSeconds(1), 1, 4096, 0, "fixture-pricing-v1",
            AiRoutePlan.CachePolicy.DISABLED, AiRoutePlan.StreamingPolicy.DISABLED,
            "NO_TRAINING_BY_DEFAULT", "UNSPECIFIED", "VISION_PARSE:LOCAL_FIXTURE", null, stage);
    }
}
