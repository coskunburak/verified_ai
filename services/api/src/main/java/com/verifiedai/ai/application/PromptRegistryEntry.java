package com.verifiedai.ai.application;

import java.util.List;

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
    PromptTrainingEligibility trainingEligibility,
    String contentSha256,
    PromptReleaseApproval approval
) {

    public PromptRegistryEntry {
        ArtifactIntegrity.requireIdentifier(promptId);
        ArtifactIntegrity.requireIdentifier(owner);
        ArtifactIntegrity.requireIdentifier(outputSchemaVersion);
        ArtifactIntegrity.requireIdentifier(compatibleRoutePolicyVersion);
        ArtifactIntegrity.requireDigest(contentSha256);

        requireText(promptVersion, "promptVersion");
        try {
            PromptVersion.requireValid(promptVersion);
        } catch (IllegalArgumentException exception) {
            throw new PromptRegistryValidationException(
                PromptRegistryFailureCode
                    .AI_PROMPT_VERSION_INVALID,
                "AI prompt version is invalid"
            );
        }

        if (capability == null || releaseStage == null || trainingEligibility == null) {
            throw new PromptRegistryValidationException(
                PromptRegistryFailureCode.AI_ARTIFACT_METADATA_INVALID,
                "AI prompt capability, release stage and training eligibility are required"
            );
        }
        requireText(owner, "owner");
        requireText(outputSchemaVersion, "outputSchemaVersion");
        requireText(promptArtifactPath, "promptArtifactPath");
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

        if (safetyNotes == null || safetyNotes.isEmpty()) {
            throw new PromptRegistryValidationException(
                PromptRegistryFailureCode.AI_ARTIFACT_METADATA_INVALID,
                "safetyNotes are required"
            );
        }

        for (String note : safetyNotes) {
            requireText(note, "safetyNotes");
        }
        safetyNotes = List.copyOf(safetyNotes);
    }

    public String owningModule() { return "ai"; }

    public String identity() {
        return promptId + ":" + promptVersion;
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
