package com.verifiedai.studyplan.api.dto;

import java.util.UUID;

public record RecommendationActionResponse(
        UUID recommendationId,
        String status,
        String nextSessionType,
        String targetSkillCode,
        String targetMistakeCode,
        String deepLinkUri
) {
}
