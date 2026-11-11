package com.verifiedai.mastery.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SkillGraphProjectionResponse(
        String schemaVersion,
        String curriculumCode,
        Instant projectedAt,
        List<SkillNodeDto> nodes,
        List<SkillEdgeDto> edges
) {
    public record SkillNodeDto(
            String skillCode,
            String title,
            BigDecimal masteryScore,
            String masteryState,
            int attemptCount,
            int consecutiveFailures,
            boolean blockedByPrerequisiteBottleneck
    ) {}

    public record SkillEdgeDto(
            String fromSkillCode,
            String toSkillCode,
            BigDecimal weight,
            boolean prerequisiteBottleneck
    ) {}
}
