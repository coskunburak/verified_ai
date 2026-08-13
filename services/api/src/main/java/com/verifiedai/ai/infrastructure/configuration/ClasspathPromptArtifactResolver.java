package com.verifiedai.ai.infrastructure.configuration;

import com.verifiedai.ai.application.PromptArtifact;
import com.verifiedai.ai.application.PromptArtifactResolver;
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

final class ClasspathPromptArtifactResolver
    implements PromptArtifactResolver {

    private static final int MAX_PROMPT_BYTES =
        64 * 1024;

    private final ResourceLoader resourceLoader;

    ClasspathPromptArtifactResolver(
        ResourceLoader resourceLoader
    ) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public PromptArtifact load(
        PromptRegistryEntry entry
    ) {
        String path =
            entry.promptArtifactPath();

        validatePath(path, entry);

        Resource resource =
            resourceLoader.getResource(
                "classpath:" + path
            );

        byte[] bytes =
            readBounded(resource, entry);

        validateUtf8(bytes, entry);

        return new PromptArtifact(
            path,
            bytes.length
        );
    }

    private static void validatePath(
        String path,
        PromptRegistryEntry entry
    ) {
        if (
            path == null
                || path.isBlank()
                || path.startsWith("/")
                || path.contains("..")
                || path.contains("\\")
                || path.contains(":")
                || path.contains("//")
                || !path.startsWith("prompts/")
                || !path.endsWith(".md")
        ) {
            throw failure(
                PromptRegistryFailureCode
                    .AI_PROMPT_ARTIFACT_PATH_INVALID,
                entry,
                "AI prompt artifact path is invalid"
            );
        }
    }

    private static byte[] readBounded(
        Resource resource,
        PromptRegistryEntry entry
    ) {
        try (InputStream input = resource.getInputStream()) {
            byte[] bytes =
                input.readNBytes(
                    MAX_PROMPT_BYTES + 1
                );

            if (bytes.length == 0) {
                throw failure(
                    PromptRegistryFailureCode
                        .AI_PROMPT_ARTIFACT_MISSING,
                    entry,
                    "AI prompt artifact is empty"
                );
            }

            if (bytes.length > MAX_PROMPT_BYTES) {
                throw failure(
                    PromptRegistryFailureCode
                        .AI_PROMPT_ARTIFACT_PATH_INVALID,
                    entry,
                    "AI prompt artifact exceeds size limit"
                );
            }

            return bytes;
        } catch (IOException exception) {
            throw failure(
                PromptRegistryFailureCode
                    .AI_PROMPT_ARTIFACT_MISSING,
                entry,
                "AI prompt artifact is missing"
            );
        }
    }

    private static void validateUtf8(
        byte[] bytes,
        PromptRegistryEntry entry
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
                    .AI_PROMPT_ARTIFACT_PATH_INVALID,
                entry,
                "AI prompt artifact is not valid UTF-8"
            );
        }
    }

    private static PromptRegistryValidationException failure(
        PromptRegistryFailureCode code,
        PromptRegistryEntry entry,
        String detail
    ) {
        return new PromptRegistryValidationException(
            code,
            detail
                + ": code="
                + code
                + " capability="
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
}
