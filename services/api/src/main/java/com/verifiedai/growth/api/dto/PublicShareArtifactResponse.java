package com.verifiedai.growth.api.dto;

import com.verifiedai.growth.application.PublicShareArtifactView;
import java.time.Instant;
import java.util.Map;

public record PublicShareArtifactResponse(
    String shareToken,
    String artifactType,
    String locale,
    String displayAlias,
    String referralCode,
    Map<String, Object> summaryPayload,
    String deepLinkUrl,
    boolean rawContentRedacted,
    boolean trainingEligible,
    int llmTokensUsed,
    Instant expiresAt,
    Instant createdAt
) {
    public static PublicShareArtifactResponse from(PublicShareArtifactView view) {
        return new PublicShareArtifactResponse(
            view.shareToken(),
            view.artifactType(),
            view.locale(),
            view.displayAlias(),
            view.referralCode(),
            view.summaryPayload(),
            view.deepLinkUrl(),
            view.rawContentRedacted(),
            view.trainingEligible(),
            view.llmTokensUsed(),
            view.expiresAt(),
            view.createdAt()
        );
    }
}
