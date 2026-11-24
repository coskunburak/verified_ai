package com.verifiedai.growth.application;

import com.verifiedai.growth.infrastructure.GrowthWriteJdbcRepository.StoredReferralAttribution;
import java.time.Instant;
import java.util.UUID;

public record ReferralAttributionResultView(
    UUID attributionId,
    String referralCode,
    String attributionSource,
    String status,
    String rewardType,
    int rewardUnits,
    String correlationId,
    boolean trainingEligible,
    int llmTokensUsed,
    Instant attributedAt,
    Instant qualifiedAt,
    Instant rewardGrantedAt,
    boolean replayed
) {
    public static ReferralAttributionResultView from(StoredReferralAttribution row, boolean replayed) {
        return new ReferralAttributionResultView(
            row.id(),
            row.referralCodeSnapshot(),
            row.attributionSource().name(),
            row.status().name(),
            row.rewardType(),
            row.rewardUnits(),
            row.correlationId(),
            row.trainingEligible(),
            0,
            row.attributedAt(),
            row.qualifiedAt(),
            row.rewardGrantedAt(),
            replayed
        );
    }
}
