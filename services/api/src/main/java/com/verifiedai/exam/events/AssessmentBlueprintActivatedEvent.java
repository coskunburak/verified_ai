package com.verifiedai.exam.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AssessmentBlueprintActivatedEvent(
        UUID blueprintId,
        UUID examDefinitionId,
        int examVersionNumber,
        String blueprintCode,
        String blueprintMode,
        int totalQuestionCount,
        String blueprintHash,
        Instant activatedAt
) {
    public AssessmentBlueprintActivatedEvent {
        Objects.requireNonNull(blueprintId, "blueprintId must not be null");
        Objects.requireNonNull(examDefinitionId, "examDefinitionId must not be null");
        Objects.requireNonNull(blueprintCode, "blueprintCode must not be null");
        Objects.requireNonNull(blueprintMode, "blueprintMode must not be null");
        Objects.requireNonNull(blueprintHash, "blueprintHash must not be null");
        Objects.requireNonNull(activatedAt, "activatedAt must not be null");
    }
}
