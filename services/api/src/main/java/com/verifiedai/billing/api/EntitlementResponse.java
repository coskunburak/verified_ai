package com.verifiedai.billing.api;

import com.verifiedai.billing.application.EntitlementResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EntitlementResponse(
    UUID id,
    UUID userId,
    String tier,
    String source,
    String status,
    Instant effectiveAt,
    Instant expiresAt,
    List<String> capabilities,
    Long version
) {
    static EntitlementResponse from(EntitlementResult result) {
        return new EntitlementResponse(
            result.id(),
            result.userId(),
            result.tier().name(),
            result.source().name(),
            result.status().name(),
            result.effectiveAt(),
            result.expiresAt(),
            result.capabilities().stream().map(Enum::name).toList(),
            result.version()
        );
    }
}
