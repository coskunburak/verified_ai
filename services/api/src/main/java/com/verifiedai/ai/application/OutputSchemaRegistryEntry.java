package com.verifiedai.ai.application;

public record OutputSchemaRegistryEntry(
    String schemaVersion,
    String artifactPath,
    String owner,
    String schemaId,
    OutputSemanticContract semanticContract,
    String contentSha256
) {

    public OutputSchemaRegistryEntry {
        ArtifactIntegrity.requireIdentifier(schemaVersion);
        ArtifactIntegrity.requireDigest(contentSha256);
        if (semanticContract == null) {
            throw new PromptRegistryValidationException(
                PromptRegistryFailureCode.AI_ARTIFACT_METADATA_INVALID,
                "AI schema semantic contract is required"
            );
        }
        requireText(schemaId, "schemaId");
        requireText(artifactPath, "artifactPath");
        requireText(owner, "owner");
    }

    private static void requireText(
        String value,
        String field
    ) {
        if (value == null || value.isBlank()) {
            throw new PromptRegistryValidationException(
                PromptRegistryFailureCode.AI_ARTIFACT_METADATA_INVALID,
                field + " is required"
            );
        }
    }
}
