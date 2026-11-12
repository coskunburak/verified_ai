package com.verifiedai.studyplan.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.studyplan.domain.LearnerRecommendationEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record LearnerRecommendationDto(
        UUID id,
        UUID userId,
        String recommendationType,
        String targetSkillCode,
        String targetMistakeCode,
        BigDecimal priorityScore,
        String rationaleCode,
        Map<String, Object> rationaleParams,
        String status,
        Instant expiresAt,
        Instant createdAt
) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static LearnerRecommendationDto fromEntity(LearnerRecommendationEntity entity) {
        Map<String, Object> params = Collections.emptyMap();
        try {
            if (entity.getRationaleParamsJson() != null && !entity.getRationaleParamsJson().isBlank()) {
                params = OBJECT_MAPPER.readValue(entity.getRationaleParamsJson(), new TypeReference<>() {});
            }
        } catch (Exception ignored) {
        }

        return new LearnerRecommendationDto(
                entity.getId(),
                entity.getUserId(),
                entity.getRecommendationType().name(),
                entity.getTargetSkillCode(),
                entity.getTargetMistakeCode(),
                entity.getPriorityScore(),
                entity.getRationaleCode().name(),
                params,
                entity.getStatus().name(),
                entity.getExpiresAt(),
                entity.getCreatedAt()
        );
    }
}
