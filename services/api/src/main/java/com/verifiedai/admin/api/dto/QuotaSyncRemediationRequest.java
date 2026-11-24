package com.verifiedai.admin.api.dto;

import java.util.UUID;

public record QuotaSyncRemediationRequest(
    UUID userId,
    String reason,
    Boolean trainingEligible
) {
}
