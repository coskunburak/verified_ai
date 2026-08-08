package com.verifiedai.profile.application;

import com.verifiedai.profile.domain.model.OnboardingStatus;
import java.time.Instant;
import java.util.UUID;

public record LearningProfileResult(
    boolean exists,
    UUID id,
    UUID userId,
    String educationLevel,
    String preferredLanguage,
    String explanationDepth,
    Integer dailyStudyMinutes,
    String timezone,
    String goalContext,
    OnboardingStatus onboardingStatus,
    Long version,
    Instant createdAt,
    Instant updatedAt
) {
    public static LearningProfileResult notStarted(UUID userId) {
        return new LearningProfileResult(
            false,
            null,
            userId,
            null,
            null,
            null,
            null,
            null,
            null,
            OnboardingStatus.NOT_STARTED,
            null,
            null,
            null
        );
    }
}
