package com.verifiedai.admin.api.dto;

import com.verifiedai.admin.application.WrongAnswerReportView;
import java.time.Instant;
import java.util.UUID;

public record WrongAnswerReportResponse(
    UUID reportId,
    UUID userId,
    UUID problemSessionId,
    String reportReason,
    String userNote,
    String verificationStatusSnapshot,
    String policyVersionSnapshot,
    String modelVersionSnapshot,
    String problemTypeSnapshot,
    String status,
    UUID reviewerUserId,
    String resolutionCode,
    String resolutionNote,
    UUID linkedRemediationAuditId,
    String correlationId,
    long version,
    boolean trainingEligible,
    Instant createdAt,
    Instant updatedAt,
    Instant resolvedAt,
    boolean replayed
) {
    public static WrongAnswerReportResponse from(WrongAnswerReportView view) {
        return new WrongAnswerReportResponse(
            view.reportId(),
            view.userId(),
            view.problemSessionId(),
            view.reportReason(),
            view.userNote(),
            view.verificationStatusSnapshot(),
            view.policyVersionSnapshot(),
            view.modelVersionSnapshot(),
            view.problemTypeSnapshot(),
            view.status(),
            view.reviewerUserId(),
            view.resolutionCode(),
            view.resolutionNote(),
            view.linkedRemediationAuditId(),
            view.correlationId(),
            view.version(),
            view.trainingEligible(),
            view.createdAt(),
            view.updatedAt(),
            view.resolvedAt(),
            view.replayed()
        );
    }
}
