package com.verifiedai.analytics.api.dto;

import java.time.Instant;
import java.util.Map;

public record AnalyticsEventIngestRequest(
    String clientEventId,
    String eventName,
    String emittedBy,
    String sourceModule,
    String entitlementTier,
    String locale,
    String appVersion,
    String sessionRefHash,
    Integer latencyMs,
    Map<String, Object> properties,
    Instant occurredAt,
    Boolean trainingEligible
) {
}
