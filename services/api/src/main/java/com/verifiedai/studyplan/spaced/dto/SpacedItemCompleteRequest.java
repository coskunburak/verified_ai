package com.verifiedai.studyplan.spaced.dto;

public record SpacedItemCompleteRequest(
        boolean passed,
        boolean independentVerification,
        long responseTimeMs,
        String triggeredMistakeCode
) {
}
