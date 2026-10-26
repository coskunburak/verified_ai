package com.verifiedai.ai.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.ai.application.ArtifactIntegrity;
import com.verifiedai.ai.application.OutputSchemaRegistry;
import com.verifiedai.ai.application.OutputSchemaRegistryEntry;
import com.verifiedai.ai.application.PromptRegistry;
import com.verifiedai.ai.application.PromptRegistryEntry;
import com.verifiedai.ai.application.PromptRegistryFailureCode;
import com.verifiedai.ai.application.PromptRegistryValidationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

final class ArtifactIntegrityTest {
    private static final PromptRegistryEntry PROMPT =
        PromptRegistry.defaults().require("vision-recognition", "v001");
    private static final OutputSchemaRegistryEntry SCHEMA =
        OutputSchemaRegistry.defaults().require("recognition-evidence-v1");

    @Test
    void existingV001PromptBytesArePinnedIndependentlyOfRegistryMetadata() throws IOException {
        Map<String, String> released = Map.of(
            "vision-recognition", "9582d46c61311704235b8541057d17fe7a9c9b8eb6a6f5c3460bbbd2e82c30cf",
            "problem-parser", "fadad19f078c124c907a0b60589b46e7b253a10dc96770c76f3783b37afc34b5",
            "problem-classifier", "97494479b05984bb532ab1d59d6f0b336030272f7c1e96d4b8ab335db86acbec"
        );
        for (var entry : released.entrySet()) {
            PromptRegistryEntry prompt = PromptRegistry.defaults().require(entry.getKey(), "v001");
            assertThat(prompt.contentSha256()).isEqualTo(entry.getValue());
            ArtifactIntegrity.verify(new DefaultResourceLoader()
                .getResource("classpath:" + prompt.promptArtifactPath()).getContentAsByteArray(), entry.getValue());
        }
    }

    @Test
    void modifiedPromptAndSchemaFailBeforeExecution() {
        var loader = bytes("tampered artifact");
        fails(() -> new ClasspathPromptArtifactResolver(loader).load(PROMPT),
            PromptRegistryFailureCode.AI_ARTIFACT_DIGEST_MISMATCH);
        fails(() -> new ClasspathOutputSchemaArtifactResolver(loader).load(SCHEMA, PROMPT),
            PromptRegistryFailureCode.AI_ARTIFACT_DIGEST_MISMATCH);
    }

    @Test
    void absentVersionCannotLoadDefaultResource() {
        PromptRegistryEntry absent = copy("v999", "prompts/vision-recognition/v999.md");
        fails(() -> new ClasspathPromptArtifactResolver(new DefaultResourceLoader()).load(absent),
            PromptRegistryFailureCode.AI_PROMPT_ARTIFACT_MISSING);
    }

    @Test
    void arbitraryReferenceAndVersionAliasCannotLoadSystemPrompt() {
        for (String path : new String[] {"prompts/../secret.md", "https://example.com/prompt.md",
            "file:/etc/passwd", "prompts/vision-recognition/v002.md", "prompts/%2e%2e/secret.md"}) {
            fails(() -> new ClasspathPromptArtifactResolver(new DefaultResourceLoader()).load(copy("v001", path)),
                PromptRegistryFailureCode.AI_PROMPT_ARTIFACT_PATH_INVALID);
        }
    }

    @Test
    void emptyOversizedAndMalformedUtf8PromptsFailClosed() {
        fails(() -> new ClasspathPromptArtifactResolver(bytes("")).load(PROMPT),
            PromptRegistryFailureCode.AI_PROMPT_ARTIFACT_MISSING);
        fails(() -> new ClasspathPromptArtifactResolver(bytes("a".repeat(65_537))).load(PROMPT),
            PromptRegistryFailureCode.AI_PROMPT_ARTIFACT_PATH_INVALID);
        fails(() -> new ClasspathPromptArtifactResolver(bytes(new byte[] {(byte) 0xc3, 0x28})).load(PROMPT),
            PromptRegistryFailureCode.AI_PROMPT_ARTIFACT_PATH_INVALID);
    }

    @Test
    void validDigestDoesNotReplaceSchemaIdentityOrJsonChecks() throws Exception {
        for (String content : new String[] {
            "not-json", "{}",
            "{\"$id\":\"wrong-schema\",\"properties\":{\"schemaVersion\":{\"const\":\"recognition-evidence-v1\"}}}",
            "{\"$id\":\"wrong\",\"$id\":\"duplicate\"}", "{} {}"
        }) {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(content.getBytes(StandardCharsets.UTF_8)));
            OutputSchemaRegistryEntry schema = new OutputSchemaRegistryEntry(SCHEMA.schemaVersion(),
                SCHEMA.artifactPath(), SCHEMA.owner(), SCHEMA.schemaId(), SCHEMA.semanticContract(), hash);
            fails(() -> new ClasspathOutputSchemaArtifactResolver(bytes(content)).load(schema, PROMPT),
                PromptRegistryFailureCode.AI_SCHEMA_CONTRACT_MISMATCH);
        }
    }

    private static PromptRegistryEntry copy(String version, String path) {
        return new PromptRegistryEntry(PROMPT.promptId(), version, PROMPT.capability(), PROMPT.owner(),
            PROMPT.outputSchemaVersion(), path, PROMPT.releaseStage(), PROMPT.evaluationBaselineReference(),
            PROMPT.evaluationReportReference(), PROMPT.safetyNotes(), PROMPT.compatibleRoutePolicyVersion(),
            PROMPT.trainingEligibility(), PROMPT.contentSha256(), null);
    }

    private static DefaultResourceLoader bytes(String content) {
        return bytes(content.getBytes(StandardCharsets.UTF_8));
    }

    private static DefaultResourceLoader bytes(byte[] content) {
        return new DefaultResourceLoader() {
            @Override
            public Resource getResource(String location) { return new ByteArrayResource(content); }
        };
    }

    private static void fails(Runnable operation, PromptRegistryFailureCode code) {
        assertThatThrownBy(operation::run).isInstanceOf(PromptRegistryValidationException.class)
            .extracting(e -> ((PromptRegistryValidationException) e).code()).isEqualTo(code);
    }
}
