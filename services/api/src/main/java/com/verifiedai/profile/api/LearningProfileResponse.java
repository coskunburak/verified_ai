package com.verifiedai.profile.api;

import com.verifiedai.profile.application.LearningProfileResult;
import java.time.Instant;
import java.util.UUID;

public record LearningProfileResponse(
    boolean exists,
    UUID id,
    UUID userId,
    String educationLevel,
    String preferredLanguage,
    String explanationDepth,
    Integer dailyStudyMinutes,
    String timezone,
    String goalContext,
    String onboardingStatus,
    Long version,
    Instant createdAt,
    Instant updatedAt
) {
    static LearningProfileResponse from(LearningProfileResult result) {
        return new LearningProfileResponse(
            result.exists(),
            result.id(),
            result.userId(),
            result.educationLevel(),
            result.preferredLanguage(),
            result.explanationDepth(),
            result.dailyStudyMinutes(),
            result.timezone(),
            result.goalContext(),
            result.onboardingStatus().name(),
            result.version(),
            result.createdAt(),
            result.updatedAt()
        );
    }
}
