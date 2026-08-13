package com.verifiedai.ai.application;

import java.util.List;
import java.util.Objects;

public record PromptRegistryEntry(
    String promptId,
    String promptVersion,
    AiCapability capability,
    String owner,
    String outputSchemaVersion,
    String promptArtifactPath,
    PromptReleaseStage releaseStage,
    String evaluationBaselineReference,
    String evaluationReportReference,
    List<String> safetyNotes,
    String compatibleRoutePolicyVersion,
    PromptTrainingEligibility trainingEligibility
) {

    public PromptRegistryEntry {
        requireText(promptId, "promptId");

        try {
            PromptVersion.requireValid(promptVersion);
        } catch (IllegalArgumentException exception) {
            throw new PromptRegistryValidationException(
                PromptRegistryFailureCode
                    .AI_PROMPT_VERSION_INVALID,
                "AI prompt version is invalid: promptId="
                    + safe(promptId)
                    + " promptVersion="
                    + safe(promptVersion)
            );
        }

        Objects.requireNonNull(capability, "capability is required");
        requireText(owner, "owner");
        requireText(outputSchemaVersion, "outputSchemaVersion");
        requireText(promptArtifactPath, "promptArtifactPath");
        Objects.requireNonNull(
            releaseStage,
            "releaseStage is required"
        );
        requireText(
            evaluationBaselineReference,
            "evaluationBaselineReference"
        );
        requireText(
            evaluationReportReference,
            "evaluationReportReference"
        );
        requireText(
            compatibleRoutePolicyVersion,
            "compatibleRoutePolicyVersion"
        );
        Objects.requireNonNull(
            trainingEligibility,
            "trainingEligibility is required"
        );

        safetyNotes =
            safetyNotes == null
                ? List.of()
                : List.copyOf(safetyNotes);

        if (safetyNotes.isEmpty()) {
            throw new IllegalArgumentException(
                "safetyNotes are required"
            );
        }

        for (String note : safetyNotes) {
            requireText(note, "safetyNotes");
        }
    }

    public String identity() {
        return promptId + ":" + promptVersion;
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

    private static String safe(
        String value
    ) {
        return value == null ? "NONE" : value;
    }
}
