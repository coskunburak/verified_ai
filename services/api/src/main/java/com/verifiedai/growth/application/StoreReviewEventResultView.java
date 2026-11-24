package com.verifiedai.growth.application;

import com.verifiedai.growth.infrastructure.GrowthWriteJdbcRepository.StoredReviewEvent;
import java.time.Instant;
import java.util.UUID;

public record StoreReviewEventResultView(
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
    public static StoreReviewEventResultView from(StoredReviewEvent row, boolean replayed) {
        return new StoreReviewEventResultView(
            row.id(),
            row.triggerMoment().name(),
            row.outcome().name(),
            row.appVersion(),
            row.locale(),
            row.correlationId(),
            row.trainingEligible(),
            0,
            row.occurredAt(),
            replayed
        );
    }
}
