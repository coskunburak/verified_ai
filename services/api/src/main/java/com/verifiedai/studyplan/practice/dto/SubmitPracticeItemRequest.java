package com.verifiedai.studyplan.practice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SubmitPracticeItemRequest(
        @JsonProperty("submittedAnswerLatex") String submittedAnswerLatex,
        @JsonProperty("clientElapsedSeconds") Integer clientElapsedSeconds,
        @JsonProperty("unlockHintRequested") Boolean unlockHintRequested
) {
    public int getEffectiveElapsedSeconds() {
        return clientElapsedSeconds != null ? clientElapsedSeconds : 0;
    }

    public boolean isUnlockHintRequested() {
        return Boolean.TRUE.equals(unlockHintRequested);
    }
}
