package com.verifiedai.ai.application;

public interface OutputSchemaArtifactResolver {

    OutputSchemaArtifact load(
        OutputSchemaRegistryEntry schema,
        PromptRegistryEntry prompt
    );
}
