package com.verifiedai.admin.api.dto;

public record RawContentGrantRequest(
    String ticketReference,
    String reason,
    Integer ttlMinutes,
    Boolean trainingEligible
) {
}
