package com.verifiedai.studyplan.api.dto;

public record DismissRecommendationRequest(String reasonCode) {
    public DismissRecommendationRequest {
        if (reasonCode == null || reasonCode.isBlank()) {
            reasonCode = "NOT_NOW";
        }
    }
}
