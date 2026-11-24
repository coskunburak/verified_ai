package com.verifiedai.growth.api.dto;

import com.verifiedai.growth.application.ShareableArtifactView;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ShareableArtifactResponse(
    UUID shareId,
    String shareToken,
    String artifactType,
    String locale,
    String displayAlias,
    String referralCode,
    Map<String, Object> summaryPayload,
    String deepLinkUrl,
    String status,
    boolean rawContentRedacted,
    long openCount,
    String correlationId,
    boolean trainingEligible,
    int llmTokensUsed,
    Instant expiresAt,
    Instant revokedAt,
    Instant createdAt,
    boolean replayed
) {
    public static ShareableArtifactResponse from(ShareableArtifactView view) {
        return new ShareableArtifactResponse(
            view.shareId(),
            view.shareToken(),
            view.artifactType(),
            view.locale(),
            view.displayAlias(),
            view.referralCode(),
            view.summaryPayload(),
            view.deepLinkUrl(),
            view.status(),
            view.rawContentRedacted(),
            view.openCount(),
            view.correlationId(),
            view.trainingEligible(),
            view.llmTokensUsed(),
            view.expiresAt(),
            view.revokedAt(),
            view.createdAt(),
            view.replayed()
        );
    }
}
