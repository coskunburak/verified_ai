package com.verifiedai.admin.api.dto;

import com.verifiedai.admin.application.AdminRawContentInspectionView.RawContentGrantView;
import java.time.Instant;
import java.util.UUID;

public record AdminRawContentGrantResponse(
    String schemaVersion,
    UUID grantId,
    UUID actorUserId,
    UUID targetUserId,
    UUID problemSessionId,
    String ticketReference,
    String reason,
    UUID auditId,
    Instant grantedAt,
    Instant expiresAt,
    boolean replayed,
    int llmTokensUsed,
    boolean trainingEligible
) {
    public static AdminRawContentGrantResponse from(RawContentGrantView view) {
        return new AdminRawContentGrantResponse(
            view.schemaVersion(),
            view.grantId(),
            view.actorUserId(),
            view.targetUserId(),
            view.problemSessionId(),
            view.ticketReference(),
            view.reason(),
            view.auditId(),
            view.grantedAt(),
            view.expiresAt(),
            view.replayed(),
            view.llmTokensUsed(),
            view.trainingEligible()
        );
    }
}
