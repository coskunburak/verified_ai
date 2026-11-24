package com.verifiedai.growth.application;

import com.verifiedai.growth.domain.GrowthContentSanitizer;
import com.verifiedai.growth.infrastructure.GrowthWriteJdbcRepository.StoredShareableArtifact;
import java.time.Instant;
import java.util.Map;

/**
 * Strictly PII-free public deep-link payload (INV-GRW-001).
 * Never exposes userId, email, objectKey, rawProblemText, ocrText, solutionSteps, or idempotencyKey.
 */
public record PublicShareArtifactView(
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
    public static PublicShareArtifactView from(StoredShareableArtifact row) {
        return new PublicShareArtifactView(
            row.shareToken(),
            row.artifactType().name(),
            row.locale(),
            row.displayAlias(),
            row.referralCode(),
            GrowthContentSanitizer.parseSummaryJson(row.summaryPayloadJson()),
            row.deepLinkUrl(),
            true,
            false,
            0,
            row.expiresAt(),
            row.createdAt()
        );
    }
}
