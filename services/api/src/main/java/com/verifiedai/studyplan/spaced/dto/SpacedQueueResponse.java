package com.verifiedai.studyplan.spaced.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SpacedQueueResponse(
        String schemaVersion,
        UUID userId,
        Instant evaluatedAt,
        int dailyCap,
        int surfacedCount,
        int interleavedSkillCount,
        boolean interleavingSatisfied,
        List<SpacedQueueItemDto> items
) {
    public static final String CURRENT_SCHEMA_VERSION = "1.0.0";
    public static final int DEFAULT_DAILY_CAP = 3;
}
