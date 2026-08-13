package com.verifiedai.ai.application;

public record OutputSchemaRegistryEntry(
    String schemaVersion,
    String artifactPath,
    String owner
) {

    public OutputSchemaRegistryEntry {
        requireText(schemaVersion, "schemaVersion");
        requireText(artifactPath, "artifactPath");
        requireText(owner, "owner");
    }

    private static void requireText(
        String value,
        String field
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                field + " is required"
            );
        }
    }
}
