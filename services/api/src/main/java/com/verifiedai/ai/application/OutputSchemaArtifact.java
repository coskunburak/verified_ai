package com.verifiedai.ai.application;

public record OutputSchemaArtifact(
    String artifactPath,
    int byteSize
) {

    public OutputSchemaArtifact {
        if (artifactPath == null || artifactPath.isBlank()) {
            throw new IllegalArgumentException(
                "artifactPath is required"
            );
        }

        if (byteSize < 0) {
            throw new IllegalArgumentException(
                "byteSize must be >= 0"
            );
        }
    }
}
