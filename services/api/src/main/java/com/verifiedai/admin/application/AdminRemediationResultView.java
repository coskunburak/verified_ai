package com.verifiedai.admin.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AdminRemediationResultView(
    String schemaVersion,
    UUID auditId,
    String action,
    String targetType,
    String targetId,
    boolean replayed,
    String outcome,
    Map<String, Object> details,
    Instant executedAt,
    int llmTokensUsed,
    boolean trainingEligible
) {
    public AdminRemediationResultView {
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
