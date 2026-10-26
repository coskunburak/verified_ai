package com.verifiedai.ai.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class OutputSchemaRegistry {

    private final Map<String, OutputSchemaRegistryEntry> entries;

    public OutputSchemaRegistry(
        Collection<OutputSchemaRegistryEntry> entries
    ) {
        Objects.requireNonNull(
            entries,
            "schema registry entries are required"
        );

        this.entries =
            entries.stream()
                .collect(
                    Collectors.toUnmodifiableMap(
                        OutputSchemaRegistryEntry::schemaVersion,
                        entry -> entry,
                        (first, second) -> {
                            throw new PromptRegistryValidationException(
                                PromptRegistryFailureCode
                                    .AI_SCHEMA_REGISTRY_DUPLICATE,
                                "AI output schema registry entry is duplicated: schemaVersion="
                                    + first.schemaVersion()
                            );
                        }
                    )
                );
    }

    public OutputSchemaRegistryEntry require(
        String schemaVersion
    ) {
        OutputSchemaRegistryEntry entry =
            schemaVersion == null ? null : entries.get(schemaVersion);

        if (entry == null) {
            throw new PromptRegistryValidationException(
                PromptRegistryFailureCode
                    .AI_SCHEMA_ARTIFACT_MISSING,
                "AI output schema registry entry is missing"
            );
        }

        return entry;
    }

    public Map<String, OutputSchemaRegistryEntry> all() {
        return entries;
    }

    public static OutputSchemaRegistry defaults() {
        return new OutputSchemaRegistry(
            List.of(
                new OutputSchemaRegistryEntry("solution-candidate-v1", "schemas/solution-candidate.schema.json",
                    "ai-platform", "urn:verified-ai:solution-candidate:v1", OutputSemanticContract.SOLUTION_CANDIDATE,
                    "c6ce80a2deb04b5aaf991cd23ad053b0210bd1d92491649f06716110fe0adb06"),
                new OutputSchemaRegistryEntry(
                    "recognition-evidence-v1",
                    "schemas/recognition-evidence.schema.json",
                    "ai-platform",
                    "https://schemas.verified-ai-learning.example/recognition-evidence.schema.json",
                    OutputSemanticContract.RECOGNITION_EVIDENCE,
                    "703cc2008597a46bb7c6d9572d34950fa1b86fc37105dcb88f4466cbd59b455f"
                ),
                new OutputSchemaRegistryEntry(
                    "problem-parse-v1",
                    "schemas/problem-parse.schema.json",
                    "ai-platform",
                    "https://schemas.verified-ai-learning.example/problem-parse.schema.json",
                    OutputSemanticContract.PROBLEM_PARSE,
                    "b1eab1750334341577d25cda7f9cbe3ec44cb0130138468753bcb2c6e69f2cc1"
                ),
                new OutputSchemaRegistryEntry(
                    "problem-classification-v1",
                    "schemas/problem-classification.schema.json",
                    "ai-platform",
                    "urn:verified-ai:problem-classification:v1",
                    OutputSemanticContract.PROBLEM_CLASSIFICATION,
                    "d4eb465ed22db04a61c046ba4d4693be7a812f6f2dac6728956ca77b67ce4268"
                )
            )
        );
    }

}
