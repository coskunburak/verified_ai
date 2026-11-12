package com.verifiedai.studyplan.spaced.dto;

import com.verifiedai.studyplan.spaced.MistakeResolutionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SpacedItemCompletionResponse(
        UUID itemId,
        BigDecimal previousHalfLifeDays,
        BigDecimal updatedHalfLifeDays,
        BigDecimal updatedRetention,
        Instant nextDueAt,
        MistakeResolutionStatus mistakeResolutionStatus
) {
}
