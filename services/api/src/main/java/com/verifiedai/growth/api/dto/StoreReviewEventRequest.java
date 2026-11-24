package com.verifiedai.growth.api.dto;

public record StoreReviewEventRequest(
    String triggerMoment,
    String outcome,
    String appVersion,
    String locale,
    Boolean trainingEligible
) {
}
