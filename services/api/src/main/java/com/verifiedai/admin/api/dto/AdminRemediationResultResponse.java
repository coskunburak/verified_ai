package com.verifiedai.admin.api.dto;

import com.verifiedai.admin.application.AdminRemediationResultView;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AdminRemediationResultResponse(
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
    public static AdminRemediationResultResponse from(AdminRemediationResultView view) {
        return new AdminRemediationResultResponse(
            view.schemaVersion(),
            view.auditId(),
            view.action(),
            view.targetType(),
            view.targetId(),
            view.replayed(),
            view.outcome(),
            view.details(),
            view.executedAt(),
            view.llmTokensUsed(),
            view.trainingEligible()
        );
    }
}
