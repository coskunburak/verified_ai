package com.verifiedai.ai.application;

import java.util.Objects;

public record AiCapabilityDescriptor(
    AiCapability capability,
    String owningModule,
    String defaultRoutePolicyVersion,
    String defaultPromptId,
    String defaultSchemaVersion,
    boolean requiresSchemaValidation,
    boolean allowsStreaming,
    boolean allowsCache,
    boolean materialAiOutput,
    boolean requiresUsageLedger,
    PrivacyClass defaultPrivacyClass,
    CostBucket defaultCostBucket
) {

    public AiCapabilityDescriptor {
        Objects.requireNonNull(capability, "capability is required");
        requireText(owningModule, "owningModule");
        requireText(
            defaultRoutePolicyVersion,
            "defaultRoutePolicyVersion"
        );
        Objects.requireNonNull(
            defaultPrivacyClass,
            "defaultPrivacyClass is required"
        );
        Objects.requireNonNull(
            defaultCostBucket,
            "defaultCostBucket is required"
        );
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

    public enum PrivacyClass {
        STUDENT_PROBLEM_CONTENT,
        DERIVED_STUDENT_CONTENT,
        OPERATIONAL_METADATA
    }

    public enum CostBucket {
        INGESTION,
        SOLVING,
        VERIFICATION,
        TUTORING,
        GENERATION
    }
}
