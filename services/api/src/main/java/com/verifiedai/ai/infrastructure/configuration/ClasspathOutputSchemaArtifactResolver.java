package com.verifiedai.ai.infrastructure.configuration;

import com.verifiedai.ai.application.OutputSchemaArtifact;
import com.verifiedai.ai.application.OutputSchemaArtifactResolver;
import com.verifiedai.ai.application.OutputSchemaRegistryEntry;
import com.verifiedai.ai.application.PromptRegistryEntry;
import com.verifiedai.ai.application.PromptRegistryFailureCode;
import com.verifiedai.ai.application.PromptRegistryValidationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

final class ClasspathOutputSchemaArtifactResolver
    implements OutputSchemaArtifactResolver {

    private static final int MAX_SCHEMA_BYTES =
        512 * 1024;

    private final ResourceLoader resourceLoader;

    ClasspathOutputSchemaArtifactResolver(
        ResourceLoader resourceLoader
    ) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public OutputSchemaArtifact load(
        OutputSchemaRegistryEntry schema,
        PromptRegistryEntry prompt
    ) {
        String path =
            schema.artifactPath();

        validatePath(path, schema, prompt);

        Resource resource =
            resourceLoader.getResource(
                "classpath:" + path
            );

        byte[] bytes =
            readBounded(resource, schema, prompt);

        validateUtf8(bytes, schema, prompt);

        return new OutputSchemaArtifact(
            path,
            bytes.length
        );
    }

    private static void validatePath(
        String path,
        OutputSchemaRegistryEntry schema,
        PromptRegistryEntry prompt
    ) {
        if (
            path == null
                || path.isBlank()
                || path.startsWith("/")
                || path.contains("..")
                || path.contains("\\")
                || path.contains(":")
                || path.contains("//")
                || !path.startsWith("schemas/")
                || !path.endsWith(".json")
        ) {
            throw failure(
                PromptRegistryFailureCode
                    .AI_SCHEMA_ARTIFACT_PATH_INVALID,
                schema,
                prompt,
                "AI output schema artifact path is invalid"
            );
        }
    }

    private static byte[] readBounded(
        Resource resource,
        OutputSchemaRegistryEntry schema,
        PromptRegistryEntry prompt
    ) {
        try (InputStream input = resource.getInputStream()) {
            byte[] bytes =
                input.readNBytes(
                    MAX_SCHEMA_BYTES + 1
                );

            if (bytes.length == 0) {
                throw failure(
                    PromptRegistryFailureCode
                        .AI_SCHEMA_ARTIFACT_MISSING,
                    schema,
                    prompt,
                    "AI output schema artifact is empty"
                );
            }

            if (bytes.length > MAX_SCHEMA_BYTES) {
                throw failure(
                    PromptRegistryFailureCode
                        .AI_SCHEMA_ARTIFACT_PATH_INVALID,
                    schema,
                    prompt,
                    "AI output schema artifact exceeds size limit"
                );
            }

            return bytes;
        } catch (IOException exception) {
            throw failure(
                PromptRegistryFailureCode
                    .AI_SCHEMA_ARTIFACT_MISSING,
                schema,
                prompt,
                "AI output schema artifact is missing"
            );
        }
    }

    private static void validateUtf8(
        byte[] bytes,
        OutputSchemaRegistryEntry schema,
        PromptRegistryEntry prompt
    ) {
        try {
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(
                    CodingErrorAction.REPORT
                )
                .onUnmappableCharacter(
                    CodingErrorAction.REPORT
                )
                .decode(
                    ByteBuffer.wrap(bytes)
                );
        } catch (CharacterCodingException exception) {
            throw failure(
                PromptRegistryFailureCode
                    .AI_SCHEMA_ARTIFACT_PATH_INVALID,
                schema,
                prompt,
                "AI output schema artifact is not valid UTF-8"
            );
        }
    }

    private static PromptRegistryValidationException failure(
        PromptRegistryFailureCode code,
        OutputSchemaRegistryEntry schema,
        PromptRegistryEntry prompt,
        String detail
    ) {
        return new PromptRegistryValidationException(
            code,
            detail
                + ": code="
                + code
                + " capability="
                + prompt.capability()
                + " promptId="
                + prompt.promptId()
                + " promptVersion="
                + prompt.promptVersion()
                + " schemaVersion="
                + schema.schemaVersion()
                + " routePolicyVersion="
                + prompt.compatibleRoutePolicyVersion()
        );
    }
}
