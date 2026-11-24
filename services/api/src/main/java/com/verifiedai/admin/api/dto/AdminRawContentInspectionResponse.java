package com.verifiedai.admin.api.dto;

import com.verifiedai.admin.application.AdminRawContentInspectionView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminRawContentInspectionResponse(
    String schemaVersion,
    UUID grantId,
    UUID problemSessionId,
    UUID targetUserId,
    String ticketReference,
    Instant expiresAt,
    UUID auditId,
    List<RawAssetArtifactDto> assets,
    List<RawParseArtifactDto> parses,
    Instant inspectedAt,
    int llmTokensUsed,
    boolean trainingEligible
) {
    public static AdminRawContentInspectionResponse from(AdminRawContentInspectionView view) {
        return new AdminRawContentInspectionResponse(
            view.schemaVersion(),
            view.grantId(),
            view.problemSessionId(),
            view.targetUserId(),
            view.ticketReference(),
            view.expiresAt(),
            view.auditId(),
            view.assets().stream()
                .map(a -> new RawAssetArtifactDto(
                    a.assetId(),
                    a.assetKind(),
                    a.status(),
                    a.objectKey(),
                    a.contentType(),
                    a.createdAt()
                ))
                .toList(),
            view.parses().stream()
                .map(p -> new RawParseArtifactDto(
                    p.parseId(),
                    p.revision(),
                    p.source(),
                    p.supportStatus(),
                    p.rawOutputJson(),
                    p.normalizedProblemJson(),
                    p.createdAt()
                ))
                .toList(),
            view.inspectedAt(),
            view.llmTokensUsed(),
            view.trainingEligible()
        );
    }

    public record RawAssetArtifactDto(
        UUID assetId,
        String assetKind,
        String status,
        String objectKey,
        String contentType,
        Instant createdAt
    ) {
    }

    public record RawParseArtifactDto(
        UUID parseId,
        int revision,
        String source,
        String supportStatus,
        String rawOutputJson,
        String normalizedProblemJson,
        Instant createdAt
    ) {
    }
}
