package com.verifiedai.ai.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.ai.application.AiCapability;
import com.verifiedai.ai.application.OutputSchemaRegistry;
import com.verifiedai.ai.application.OutputSchemaRegistryEntry;
import com.verifiedai.ai.application.PromptRegistry;
import com.verifiedai.ai.application.PromptRegistryEntry;
import com.verifiedai.ai.application.PromptRegistryFailureCode;
import com.verifiedai.ai.application.PromptRegistryValidationException;
import com.verifiedai.ai.application.PromptReleaseStage;
import com.verifiedai.ai.application.PromptTrainingEligibility;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

final class ClasspathArtifactResolverTest {

    private final ClasspathPromptArtifactResolver prompts =
        new ClasspathPromptArtifactResolver(
            new DefaultResourceLoader()
        );

    private final ClasspathOutputSchemaArtifactResolver schemas =
        new ClasspathOutputSchemaArtifactResolver(
            new DefaultResourceLoader()
        );

    @Test
    void resolvesDefaultClassifierPromptArtifact() {
        PromptRegistryEntry entry =
            PromptRegistry
                .defaults()
                .require(
                    "problem-classifier",
                    "v001"
                );

        assertThat(
            prompts.load(entry).byteSize()
        ).isGreaterThan(0);
    }

    @Test
    void resolvesCanonicalClassificationSchemaArtifact() {
        PromptRegistryEntry prompt =
            PromptRegistry
                .defaults()
                .require(
                    "problem-classifier",
                    "v001"
                );

        OutputSchemaRegistryEntry schema =
            OutputSchemaRegistry
                .defaults()
                .require(
                    "problem-classification-v1"
                );

        assertThat(
            schemas.load(schema, prompt).byteSize()
        ).isGreaterThan(0);
    }

    @Test
    void promptArtifactPathCannotTraverse() {
        PromptRegistryEntry entry =
            entryWithPromptPath(
                "prompts/../../secret.md"
            );

        assertFailure(
            () -> prompts.load(entry),
            PromptRegistryFailureCode
                .AI_PROMPT_ARTIFACT_PATH_INVALID
        );
    }

    @Test
    void promptArtifactPathCannotUseArbitraryUrl() {
        PromptRegistryEntry entry =
            entryWithPromptPath(
                "file:///etc/passwd"
            );

        assertFailure(
            () -> prompts.load(entry),
            PromptRegistryFailureCode
                .AI_PROMPT_ARTIFACT_PATH_INVALID
        );
    }

    @Test
    void schemaArtifactPathCannotTraverse() {
        PromptRegistryEntry prompt =
            PromptRegistry
                .defaults()
                .require(
                    "problem-classifier",
                    "v001"
                );

        OutputSchemaRegistryEntry schema =
            new OutputSchemaRegistryEntry(
                "problem-classification-v1",
                "schemas/../../secret.json",
                "ai-platform"
            );

        assertFailure(
            () -> schemas.load(schema, prompt),
            PromptRegistryFailureCode
                .AI_SCHEMA_ARTIFACT_PATH_INVALID
        );
    }

    @Test
    void staleDoubleExtensionSchemaPathIsNotRegistered() {
        assertThat(
            OutputSchemaRegistry
                .defaults()
                .all()
                .values()
        ).noneMatch(schema ->
            schema
                .artifactPath()
                .endsWith(".json.json")
        );
    }

    @Test
    void defaultsResolveAllExecutableArtifacts() {
        assertThatCode(() -> {
            for (
                PromptRegistryEntry entry :
                PromptRegistry
                    .defaults()
                    .all()
                    .values()
            ) {
                prompts.load(entry);
                schemas.load(
                    OutputSchemaRegistry
                        .defaults()
                        .require(
                            entry.outputSchemaVersion()
                        ),
                    entry
                );
            }
        }).doesNotThrowAnyException();
    }

    private static PromptRegistryEntry entryWithPromptPath(
        String path
    ) {
        return new PromptRegistryEntry(
            "problem-classifier",
            "v001",
            AiCapability.PROBLEM_CLASSIFY,
            "ai-platform",
            "problem-classification-v1",
            path,
            PromptReleaseStage.LOCAL_ONLY,
            "evaluations/baselines/production-ingestion-v1.json",
            "CONNECTED_PROVIDER_EVAL_BLOCKED_NO_APPROVED_PROVIDER_ROUTE",
            List.of("safe note"),
            "problem-classifier-route-v1",
            PromptTrainingEligibility.NOT_ELIGIBLE
        );
    }

    private static void assertFailure(
        Runnable runnable,
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
}
