package com.verifiedai.tutoring.domain;

import java.util.UUID;

public record MicroLessonCompletionRequest(
        String locale,
        String triggerSource,
        UUID sourceAttemptId,
        String selectedOptionId,
        int dwellTimeMs,
        String idempotencyKey
) {}
