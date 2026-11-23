package com.verifiedai.analytics.application;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable server-side analytics event command using exclusively JDK primitive/standard types
 * so peer modules never depend on analytics.domain (INV-ANL-007).
 */
public record RecordAnalyticsEventCommand(
    UUID userId,
    String clientEventId,
    String eventName,
    String sourceModule,
    String entitlementTier,
    String locale,
    String appVersion,
    String sessionRefHash,
    Integer latencyMs,
    long aiCostMicros,
    Map<String, Object> properties,
    String correlationId,
    Instant occurredAt
) {
    public RecordAnalyticsEventCommand {
        properties = properties == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    public static RecordAnalyticsEventCommand of(
        UUID userId,
        String clientEventId,
        String eventName,
        String sourceModule,
        String entitlementTier,
        String locale,
        Integer latencyMs,
        long aiCostMicros,
        Map<String, Object> properties,
        String correlationId,
        Instant occurredAt
    ) {
        return new RecordAnalyticsEventCommand(
            Objects.requireNonNull(userId, "userId must not be null"),
            clientEventId,
            eventName,
            sourceModule,
            entitlementTier,
            locale,
            null,
            null,
            latencyMs,
            aiCostMicros,
            properties,
            correlationId,
            occurredAt
        );
    }
}
