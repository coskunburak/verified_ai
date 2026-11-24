package com.verifiedai.admin.api.dto;

import java.util.UUID;

public record BillingReconcileRemediationRequest(
    UUID userId,
    String transactionId,
    String reason,
    Boolean trainingEligible
) {
}
