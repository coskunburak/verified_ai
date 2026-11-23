package com.verifiedai.analytics.api.dto;

import com.verifiedai.analytics.application.AnalyticsEventIngestionService.BatchIngestView;
import com.verifiedai.analytics.application.AnalyticsIngestOutcome;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AnalyticsEventIngestResponse(
    String schemaVersion,
    int acceptedCount,
    int replayedCount,
    int rejectedCount,
    List<ResultItem> results,
    int llmTokensUsed,
    boolean trainingEligible,
    Instant processedAt
) {
    public static AnalyticsEventIngestResponse from(BatchIngestView view) {
        return new AnalyticsEventIngestResponse(
            view.schemaVersion(),
            view.acceptedCount(),
            view.replayedCount(),
            view.rejectedCount(),
            view.results().stream().map(ResultItem::from).toList(),
            view.llmTokensUsed(),
            view.trainingEligible(),
            view.processedAt()
        );
    }

    public record ResultItem(
        String clientEventId,
        String eventName,
        String status,
        UUID eventId,
        String errorCode,
        Instant processedAt
    ) {
        public static ResultItem from(AnalyticsIngestOutcome outcome) {
            return new ResultItem(
                outcome.clientEventId(),
                outcome.eventName(),
                outcome.status(),
                outcome.eventId(),
                outcome.errorCode(),
                outcome.processedAt()
            );
        }
    }
}
