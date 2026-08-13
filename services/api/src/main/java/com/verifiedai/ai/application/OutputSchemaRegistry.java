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
            entries.get(schemaVersion);

        if (entry == null) {
            throw new PromptRegistryValidationException(
                PromptRegistryFailureCode
                    .AI_SCHEMA_ARTIFACT_MISSING,
                "AI output schema registry entry is missing: schemaVersion="
                    + safe(schemaVersion)
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
                new OutputSchemaRegistryEntry(
                    "recognition-evidence-v1",
                    "schemas/recognition-evidence.schema.json",
                    "ai-platform"
                ),
                new OutputSchemaRegistryEntry(
                    "problem-parse-v1",
                    "schemas/problem-parse.schema.json",
                    "ai-platform"
                ),
                new OutputSchemaRegistryEntry(
                    "problem-classification-v1",
                    "schemas/problem-classification.schema.json",
                    "ai-platform"
                )
            )
        );
    }

    private static String safe(
        String value
    ) {
        return value == null ? "NONE" : value;
    }
}
