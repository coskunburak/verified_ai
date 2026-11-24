package com.verifiedai.admin.api.dto;

import java.util.UUID;

public record WrongAnswerReportReviewRequest(
    String status,
    String resolutionCode,
    String resolutionNote,
    String reason,
    UUID linkedRemediationAuditId,
    Long expectedVersion,
    Boolean trainingEligible
) {
}
