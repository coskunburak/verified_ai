package com.verifiedai.exam.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AssessmentAssembledEvent(
        UUID assembledAssessmentId,
        UUID userId,
        UUID blueprintId,
        UUID examDefinitionId,
        int examVersionNumber,
        String assemblySeed,
        String reproducibilityHash,
        int totalQuestionCount,
        int effectiveDurationSeconds,
        Instant assembledAt
) {
    public AssessmentAssembledEvent {
        Objects.requireNonNull(assembledAssessmentId, "assembledAssessmentId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(blueprintId, "blueprintId must not be null");
        Objects.requireNonNull(examDefinitionId, "examDefinitionId must not be null");
        Objects.requireNonNull(assemblySeed, "assemblySeed must not be null");
        Objects.requireNonNull(reproducibilityHash, "reproducibilityHash must not be null");
        Objects.requireNonNull(assembledAt, "assembledAt must not be null");
    }
}
