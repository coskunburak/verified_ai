package com.verifiedai.admin.api.dto;

import java.util.UUID;

public record WrongAnswerReportCreateRequest(
    UUID problemSessionId,
    String reportReason,
    String userNote,
    Boolean trainingEligible
) {
}
