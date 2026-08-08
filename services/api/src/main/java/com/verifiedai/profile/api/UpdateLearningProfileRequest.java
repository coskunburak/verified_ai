package com.verifiedai.profile.api;

public record UpdateLearningProfileRequest(
    String educationLevel,
    String preferredLanguage,
    String explanationDepth,
    Integer dailyStudyMinutes,
    String timezone,
    String goalContext,
    Boolean completeOnboarding,
    Long expectedVersion
) {
}
