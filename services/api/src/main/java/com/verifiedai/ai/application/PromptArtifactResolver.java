package com.verifiedai.ai.application;

public interface PromptArtifactResolver {

    PromptArtifact load(
        PromptRegistryEntry entry
    );
}
