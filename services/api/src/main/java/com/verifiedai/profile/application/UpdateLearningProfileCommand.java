package com.verifiedai.profile.application;

public record UpdateLearningProfileCommand(
    String educationLevel,
    String preferredLanguage,
    String explanationDepth,
    Integer dailyStudyMinutes,
    String timezone,
    String goalContext,
    boolean completeOnboarding,
    Long expectedVersion
) {
}
