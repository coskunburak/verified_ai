package com.verifiedai.studyplan.practice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record VerificationLockStateDto(
        @JsonProperty("isLocked") boolean isLocked,
        @JsonProperty("lockDurationSeconds") int lockDurationSeconds,
        @JsonProperty("remainingLockSeconds") int remainingLockSeconds,
        @JsonProperty("unlockedAt") Instant unlockedAt
) {
}
