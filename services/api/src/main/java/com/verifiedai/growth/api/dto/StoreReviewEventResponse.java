package com.verifiedai.growth.api.dto;

import com.verifiedai.growth.application.StoreReviewEventResultView;
import java.time.Instant;
import java.util.UUID;

public record StoreReviewEventResponse(
    UUID eventId,
    String triggerMoment,
    String outcome,
    String appVersion,
    String locale,
    String correlationId,
    boolean trainingEligible,
    int llmTokensUsed,
    Instant occurredAt,
    boolean replayed
) {
    public static StoreReviewEventResponse from(StoreReviewEventResultView view) {
        return new StoreReviewEventResponse(
            view.eventId(),
            view.triggerMoment(),
            view.outcome(),
            view.appVersion(),
            view.locale(),
            view.correlationId(),
            view.trainingEligible(),
            view.llmTokensUsed(),
            view.occurredAt(),
            view.replayed()
        );
    }
}
