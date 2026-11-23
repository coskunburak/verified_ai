package com.verifiedai.analytics.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Outcome of recording or ingesting a single analytics event.
 */
public record AnalyticsIngestOutcome(
    String clientEventId,
    String eventName,
    String status,
    UUID eventId,
    String errorCode,
    Instant processedAt
) {
}
