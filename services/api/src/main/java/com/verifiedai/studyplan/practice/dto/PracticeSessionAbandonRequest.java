package com.verifiedai.studyplan.practice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PracticeSessionAbandonRequest(
        @JsonProperty("reasonNote") String reasonNote
) {
}
