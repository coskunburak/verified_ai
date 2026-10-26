package com.verifiedai.ai.application;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Byte-level integrity for immutable, repository-owned artifacts. */
public final class ArtifactIntegrity {
    private ArtifactIntegrity() {}

    public static void requireDigest(String digest) {
        if (digest == null || !digest.matches("[a-f0-9]{64}")) {
            throw new PromptRegistryValidationException(
                PromptRegistryFailureCode.AI_ARTIFACT_METADATA_INVALID,
                "AI artifact SHA-256 metadata is invalid"
            );
        }
    }

    public static void verify(byte[] content, String expected) {
        requireDigest(expected);
        try {
            String actual = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(content)
            );
            if (!actual.equals(expected)) {
                throw new PromptRegistryValidationException(
                    PromptRegistryFailureCode.AI_ARTIFACT_DIGEST_MISMATCH,
                    "AI artifact SHA-256 does not match its immutable registry entry"
                );
            }
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static void requireIdentifier(String value) {
        if (value == null || !value.matches("[a-z][a-z0-9-]{0,95}")) {
            throw new PromptRegistryValidationException(
                PromptRegistryFailureCode.AI_ARTIFACT_METADATA_INVALID,
                "AI artifact identifier is invalid"
            );
        }
    }
}
