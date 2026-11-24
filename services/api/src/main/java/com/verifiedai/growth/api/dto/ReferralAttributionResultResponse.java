package com.verifiedai.growth.api.dto;

import com.verifiedai.growth.application.ReferralAttributionResultView;
import java.time.Instant;
import java.util.UUID;

public record ReferralAttributionResultResponse(
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
    public static ReferralAttributionResultResponse from(ReferralAttributionResultView view) {
        return new ReferralAttributionResultResponse(
            view.attributionId(),
            view.referralCode(),
            view.attributionSource(),
            view.status(),
            view.rewardType(),
            view.rewardUnits(),
            view.correlationId(),
            view.trainingEligible(),
            view.llmTokensUsed(),
            view.attributedAt(),
            view.qualifiedAt(),
            view.rewardGrantedAt(),
            view.replayed()
        );
    }
}
