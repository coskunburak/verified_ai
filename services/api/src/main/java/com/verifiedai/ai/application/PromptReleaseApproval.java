package com.verifiedai.ai.application;

import java.time.Instant;

/** Git-reviewed evidence attestation; never inferred from a deployment flag. */
public record PromptReleaseApproval(
    String promptSha256,
    String schemaSha256,
    String baselineReference,
    String evaluationReportReference,
    String approvalReference,
    String approvedBy,
    Instant approvedAt
) {
    public PromptReleaseApproval {
        ArtifactIntegrity.requireDigest(promptSha256);
        ArtifactIntegrity.requireDigest(schemaSha256);
        requireReference(baselineReference, "evaluations/");
        requireReference(evaluationReportReference, "evaluations/");
        requireReference(approvalReference, "docs/");
        ArtifactIntegrity.requireIdentifier(approvedBy);
        if (approvedAt == null) {
            throw new PromptRegistryValidationException(
                PromptRegistryFailureCode.AI_ARTIFACT_METADATA_INVALID,
                "AI release approval timestamp is required"
            );
        }
    }

    private static void requireReference(String value, String prefix) {
        if (value == null || !value.startsWith(prefix)
            || !value.matches("[A-Za-z0-9_./-]+")
            || value.contains("..") || value.contains("//")
            || !(value.endsWith(".json") || value.endsWith(".md"))) {
            throw new PromptRegistryValidationException(
                PromptRegistryFailureCode.AI_ARTIFACT_METADATA_INVALID,
                "AI release evidence must reference a repository artifact"
            );
        }
    }
}
