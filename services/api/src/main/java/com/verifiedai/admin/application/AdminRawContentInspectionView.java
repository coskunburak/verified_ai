package com.verifiedai.admin.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminRawContentInspectionView(
    String schemaVersion,
    UUID grantId,
    UUID problemSessionId,
    UUID targetUserId,
    String ticketReference,
    Instant expiresAt,
    UUID auditId,
    List<RawAssetArtifactView> assets,
    List<RawParseArtifactView> parses,
    Instant inspectedAt,
    int llmTokensUsed,
    boolean trainingEligible
) {
    public AdminRawContentInspectionView {
        assets = assets == null ? List.of() : List.copyOf(assets);
        parses = parses == null ? List.of() : List.copyOf(parses);
    }

    public record RawAssetArtifactView(
        UUID assetId,
        String assetKind,
        String status,
        String objectKey,
        String contentType,
        Instant createdAt
    ) {
    }

    public record RawParseArtifactView(
        UUID parseId,
        int revision,
        String source,
        String supportStatus,
        String rawOutputJson,
        String normalizedProblemJson,
        Instant createdAt
    ) {
    }

    public record RawContentGrantView(
        String schemaVersion,
        UUID grantId,
        UUID actorUserId,
        UUID targetUserId,
        UUID problemSessionId,
        String ticketReference,
        String reason,
        UUID auditId,
        Instant grantedAt,
        Instant expiresAt,
        boolean replayed,
        int llmTokensUsed,
        boolean trainingEligible
    ) {
    }
}
