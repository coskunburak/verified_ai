package com.verifiedai.studyplan.practice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.verifiedai.studyplan.practice.PracticeSessionEntity;
import com.verifiedai.studyplan.practice.PracticeSessionMode;
import com.verifiedai.studyplan.practice.PracticeSessionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PracticeSessionResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("userId") UUID userId,
        @JsonProperty("sourceRecommendationId") UUID sourceRecommendationId,
        @JsonProperty("sessionMode") PracticeSessionMode sessionMode,
        @JsonProperty("status") PracticeSessionStatus status,
        @JsonProperty("targetSkillId") String targetSkillId,
        @JsonProperty("activeTrapFamily") String activeTrapFamily,
        @JsonProperty("learnerMasterySnapshot") BigDecimal learnerMasterySnapshot,
        @JsonProperty("totalItems") int totalItems,
        @JsonProperty("completedItems") int completedItems,
        @JsonProperty("verifiedCorrectItems") int verifiedCorrectItems,
        @JsonProperty("trapClosureAchieved") boolean trapClosureAchieved,
        @JsonProperty("startedAt") Instant startedAt,
        @JsonProperty("completedAt") Instant completedAt,
        @JsonProperty("abandonedAt") Instant abandonedAt,
        @JsonProperty("updatedAt") Instant updatedAt,
        @JsonProperty("items") List<PracticeSessionItemDto> items
) {
    public static PracticeSessionResponse fromEntity(PracticeSessionEntity entity) {
        List<PracticeSessionItemDto> itemDtos = entity.getItems().stream()
                .map(PracticeSessionItemDto::fromEntity)
                .toList();

        return new PracticeSessionResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getSourceRecommendationId(),
                entity.getSessionMode(),
                entity.getStatus(),
                entity.getTargetSkillId(),
                entity.getActiveTrapFamily(),
                entity.getLearnerMasterySnapshot(),
                entity.getTotalItems(),
                entity.getCompletedItems(),
                entity.getVerifiedCorrectItems(),
                entity.isTrapClosureAchieved(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getAbandonedAt(),
                entity.getUpdatedAt(),
                itemDtos
        );
    }
}
