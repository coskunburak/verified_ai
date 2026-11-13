package com.verifiedai.tutoring.domain;

import java.time.Instant;
import java.util.UUID;

public record MicroLessonCompletionRecord(
        UUID completionId,
        UUID userId,
        UUID cardId,
        String mistakeCode,
        String locale,
        UUID sourceAttemptId,
        MicroLessonTriggerSource triggerSource,
        String selectedOptionId,
        boolean quickCheckPassed,
        int dwellTimeMs,
        String idempotencyKey,
        boolean trainingEligible,
        Instant completedAt
) {}
