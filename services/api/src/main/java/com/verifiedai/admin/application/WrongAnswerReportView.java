package com.verifiedai.admin.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WrongAnswerReportView(
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
    public record WrongAnswerReportQueueView(
        String schemaVersion,
        int reportCount,
        List<WrongAnswerReportView> reports,
        Instant generatedAt,
        int llmTokensUsed,
        boolean trainingEligible
    ) {
        public WrongAnswerReportQueueView {
            reports = reports == null ? List.of() : List.copyOf(reports);
        }
    }
}
