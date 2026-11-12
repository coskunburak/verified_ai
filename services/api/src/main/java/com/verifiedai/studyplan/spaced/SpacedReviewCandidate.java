package com.verifiedai.studyplan.spaced;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SpacedReviewCandidate(
        UUID itemId,
        String canonicalSkillCode,
        String skillDisplayName,
        SpacedTriggerType triggerType,
        String targetMistakeCode,
        BigDecimal estimatedRetention,
        BigDecimal halfLifeDays,
        Instant dueAt,
        UUID assignedTwinId,
        String promptPreviewLatex,
        SpacedQueueStatus status,
        String pedagogicalBadgeKey
) {
}
