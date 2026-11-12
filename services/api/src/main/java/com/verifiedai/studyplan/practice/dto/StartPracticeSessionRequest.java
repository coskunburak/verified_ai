package com.verifiedai.studyplan.practice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.verifiedai.studyplan.practice.PracticeSessionMode;

import java.util.UUID;

public record StartPracticeSessionRequest(
        @JsonProperty("recommendationId") UUID recommendationId,
        @JsonProperty(value = "targetSkillId", required = true) String targetSkillId,
        @JsonProperty("sessionMode") PracticeSessionMode sessionMode,
        @JsonProperty("locale") String locale
) {
    public PracticeSessionMode getEffectiveSessionMode() {
        return sessionMode != null ? sessionMode : PracticeSessionMode.DAILY_1_2_1;
    }
}
