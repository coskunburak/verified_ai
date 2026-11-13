package com.verifiedai.tutoring.domain;

import java.time.Instant;
import java.util.UUID;

public record MicroLessonCompletionResult(
        String schemaVersion,
        UUID completionId,
        UUID cardId,
        String mistakeCode,
        String locale,
        boolean quickCheckPassed,
        int dwellTimeMs,
        Instant completedAt,
        boolean idempotentReplay,
        boolean trainingEligible
) {}
