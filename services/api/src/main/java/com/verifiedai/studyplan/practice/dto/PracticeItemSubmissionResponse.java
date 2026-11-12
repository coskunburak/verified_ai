package com.verifiedai.studyplan.practice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.verifiedai.studyplan.practice.PracticeItemStatus;

public record PracticeItemSubmissionResponse(
        @JsonProperty("itemStatus") PracticeItemStatus itemStatus,
        @JsonProperty("isCorrect") boolean isCorrect,
        @JsonProperty("trapTriggered") boolean trapTriggered,
        @JsonProperty("trapDiagnosticKey") String trapDiagnosticKey,
        @JsonProperty("trapDiagnosticMessageTr") String trapDiagnosticMessageTr,
        @JsonProperty("trapDiagnosticMessageEn") String trapDiagnosticMessageEn,
        @JsonProperty("verificationLockActive") boolean verificationLockActive,
        @JsonProperty("remainingLockSeconds") int remainingLockSeconds,
        @JsonProperty("trapClosureAchieved") boolean trapClosureAchieved,
        @JsonProperty("session") PracticeSessionResponse session
) {
}
