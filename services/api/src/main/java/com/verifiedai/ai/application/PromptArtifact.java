package com.verifiedai.ai.application;

public record PromptArtifact(
    String artifactPath,
    int byteSize
) {

    public PromptArtifact {
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
