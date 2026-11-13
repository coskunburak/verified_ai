package com.verifiedai.tutoring.domain;

import java.util.UUID;

public record MicroLessonCard(
        String schemaVersion,
        UUID cardId,
        String mistakeCode,
        String locale,
        String title,
        String focusBadgeLabel,
        int estimatedDurationSeconds,
        MicroLessonTriggerSource triggerSource,
        UUID triggeredByAttemptId,
        MicroLessonCoreRule coreRule,
        MicroLessonCounterexample brokenVsValidCounterexample,
        MicroLessonQuickCheck interactiveQuickCheck,
        String verificationStatus,
        String verifierVersion,
        String verificationHash
) {
    public MicroLessonCard withTriggerContext(MicroLessonTriggerSource trigger, UUID attemptId) {
        return new MicroLessonCard(
                schemaVersion,
                cardId,
                mistakeCode,
                locale,
                title,
                focusBadgeLabel,
                estimatedDurationSeconds,
                trigger,
                attemptId,
                coreRule,
                brokenVsValidCounterexample,
                interactiveQuickCheck,
                verificationStatus,
                verifierVersion,
                verificationHash
        );
    }
}
