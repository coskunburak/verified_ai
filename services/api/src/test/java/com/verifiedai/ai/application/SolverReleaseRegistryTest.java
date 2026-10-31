package com.verifiedai.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class SolverReleaseRegistryTest {
    private static final PromptRegistryEntry ORIGINAL = PromptRegistry.defaults().require("solver", "v001");
    private static final OutputSchemaRegistryEntry SCHEMA = OutputSchemaRegistry.defaults().require("solution-candidate-v1");

    @Test
    void referencesAndMatchingPromptHashesAloneCannotApproveSolverRelease() {
        for (PromptReleaseStage stage : List.of(PromptReleaseStage.STAGING, PromptReleaseStage.PRODUCTION)) {
            PromptRegistryEntry prompt = released(stage);
            var validator = new PromptRegistryValidator(new PromptRegistry(List.of(prompt)), OutputSchemaRegistry.defaults(),
                entry -> new PromptArtifact("fixture", 1), (schema, entry) -> new OutputSchemaArtifact("fixture", 1));
            assertThatThrownBy(() -> validator.validate(route(stage), stage == PromptReleaseStage.STAGING ? "staging" : "production"))
                .isInstanceOf(PromptRegistryValidationException.class)
                .extracting(error -> ((PromptRegistryValidationException) error).code())
                .isEqualTo(PromptRegistryFailureCode.AI_SOLVER_RELEASE_EVIDENCE_INVALID);
        }
    }

    @Test
    void releaseEvidenceBoundaryIsMandatoryEvenWhenEnvironmentIsLocal() {
        AtomicInteger invoked = new AtomicInteger();
        PromptRegistryEntry prompt = released(PromptReleaseStage.PRODUCTION);
        var validator = new PromptRegistryValidator(new PromptRegistry(List.of(prompt)), OutputSchemaRegistry.defaults(),
            entry -> new PromptArtifact("fixture", 1), (schema, entry) -> new OutputSchemaArtifact("fixture", 1),
            (entry, schema, plan) -> {
                invoked.incrementAndGet();
                SolverReleaseEvidenceValidator.unavailable().validate(entry, schema, plan);
            });
        assertThatThrownBy(() -> validator.validate(route(PromptReleaseStage.PRODUCTION), "local"))
            .isInstanceOf(PromptRegistryValidationException.class);
        assertThat(invoked.get()).isEqualTo(1);
    }

    @Test
    void deterministicFixturesKeepTheirExistingReleaseBoundary() {
        var validator = new PromptRegistryValidator(PromptRegistry.defaults(), OutputSchemaRegistry.defaults(),
            entry -> new PromptArtifact("fixture", 1), (schema, entry) -> new OutputSchemaArtifact("fixture", 1),
            (entry, schema, plan) -> { throw new AssertionError("Fixture cannot request production evidence"); });
        assertThatCode(() -> validator.validate(route(PromptReleaseStage.LOCAL_ONLY), "test"))
            .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(route(PromptReleaseStage.LOCAL_ONLY), "production"))
            .isInstanceOf(PromptRegistryValidationException.class);
    }

    @Test
    void authorizationReferenceAndDigestMustBePresentTogetherAndCannotEscapeArtifacts() {
        for (String path : List.of("https://example.com/release.json", "evaluations/../release.json")) {
            assertThatThrownBy(() -> approval(path, "a".repeat(64))).isInstanceOf(PromptRegistryValidationException.class);
        }
        assertThatThrownBy(() -> approval("evaluations/release.json", null)).isInstanceOf(PromptRegistryValidationException.class);
        assertThatThrownBy(() -> approval(null, "a".repeat(64))).isInstanceOf(PromptRegistryValidationException.class);
    }

    private static PromptReleaseApproval approval(String reference, String digest) {
        return new PromptReleaseApproval(ORIGINAL.contentSha256(), SCHEMA.contentSha256(), "evaluations/baselines/release.json",
            "evaluations/reports/release.json", "docs/approvals/release.md", "owner-reviewer",
            Instant.parse("2026-01-01T00:00:00Z"), reference, digest);
    }

    private static PromptRegistryEntry released(PromptReleaseStage stage) {
        return new PromptRegistryEntry(ORIGINAL.promptId(), ORIGINAL.promptVersion(), ORIGINAL.capability(), ORIGINAL.owner(),
            ORIGINAL.outputSchemaVersion(), ORIGINAL.promptArtifactPath(), stage, "evaluations/baselines/release.json",
            "evaluations/reports/release.json", ORIGINAL.safetyNotes(), ORIGINAL.compatibleRoutePolicyVersion(),
            ORIGINAL.trainingEligibility(), ORIGINAL.contentSha256(), approval(null, null));
    }

    private static AiRoutePlan route(PromptReleaseStage stage) {
        return new AiRoutePlan(AiCapability.SOLVE, "solve-route-v1", "solver-route-v1",
            new AiRouteTarget("LOCAL_FIXTURE", "local-fixture-solver-v1"), List.of(), "solver", "v001", "solution-candidate-v1",
            Duration.ofSeconds(1), 1, 4096, 0, "fixture-pricing-v1", AiRoutePlan.CachePolicy.DISABLED,
            AiRoutePlan.StreamingPolicy.DISABLED, "NO_TRAINING_BY_DEFAULT", "UNSPECIFIED", "SOLVE:LOCAL_FIXTURE",
            null, stage == PromptReleaseStage.STAGING ? AiRoutePlan.ReleaseStage.STAGING :
                stage == PromptReleaseStage.PRODUCTION ? AiRoutePlan.ReleaseStage.PRODUCTION : AiRoutePlan.ReleaseStage.LOCAL_ONLY);
    }
}
