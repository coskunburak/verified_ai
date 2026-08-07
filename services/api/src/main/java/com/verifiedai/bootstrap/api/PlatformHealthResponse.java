package com.verifiedai.bootstrap.api;

import java.time.Instant;

public record PlatformHealthResponse(
    String status,
    String service,
    String environment,
    String correlationId,
    Instant timestamp
) {
    static PlatformHealthResponse up(String environment, String correlationId, Instant timestamp) {
        return new PlatformHealthResponse("UP", "verified-ai-api", environment, correlationId, timestamp);
    }

    static PlatformHealthResponse ready(String environment, String correlationId, Instant timestamp) {
        return new PlatformHealthResponse("READY", "verified-ai-api", environment, correlationId, timestamp);
    }

    static PlatformHealthResponse notReady(String environment, String correlationId, Instant timestamp) {
        return new PlatformHealthResponse("NOT_READY", "verified-ai-api", environment, correlationId, timestamp);
    }
}
