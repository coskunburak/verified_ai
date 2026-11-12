package com.verifiedai.studyplan.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LearnerRecommendationsResponse(
        UUID userId,
        Instant generatedAt,
        String deterministicEngineVersion,
        double llmTokenCostUsd,
        List<LearnerRecommendationDto> recommendations
) {
    public static final String ENGINE_VERSION = "NBA-v1.0";
    public static final double TOKEN_COST_USD = 0.0;

    public static LearnerRecommendationsResponse of(UUID userId, Instant generatedAt, List<LearnerRecommendationDto> recommendations) {
        return new LearnerRecommendationsResponse(
                userId,
                generatedAt,
                ENGINE_VERSION,
                TOKEN_COST_USD,
                recommendations
        );
    }
}
