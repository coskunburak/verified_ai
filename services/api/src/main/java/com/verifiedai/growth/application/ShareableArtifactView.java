package com.verifiedai.growth.application;

import com.verifiedai.growth.domain.GrowthContentSanitizer;
import com.verifiedai.growth.infrastructure.GrowthWriteJdbcRepository.StoredShareableArtifact;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ShareableArtifactView(
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
    public static ShareableArtifactView from(StoredShareableArtifact row, boolean replayed) {
        return new ShareableArtifactView(
            row.id(),
            row.shareToken(),
            row.artifactType().name(),
            row.locale(),
            row.displayAlias(),
            row.referralCode(),
            GrowthContentSanitizer.parseSummaryJson(row.summaryPayloadJson()),
            row.deepLinkUrl(),
            row.status().name(),
            row.rawContentRedacted(),
            row.openCount(),
            row.correlationId(),
            row.trainingEligible(),
            0,
            row.expiresAt(),
            row.revokedAt(),
            row.createdAt(),
            replayed
        );
    }
}
