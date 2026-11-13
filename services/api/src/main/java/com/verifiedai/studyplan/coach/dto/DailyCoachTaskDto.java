package com.verifiedai.studyplan.coach.dto;

import com.verifiedai.studyplan.coach.DailyCoachTaskStatus;
import com.verifiedai.studyplan.coach.DailyCoachTaskType;

import java.time.Instant;
import java.util.UUID;

public record DailyCoachTaskDto(
        UUID taskId,
        int taskOrder,
        DailyCoachTaskType taskType,
        String targetSkillCode,
        String targetMistakeCode,
        String assignedTwinId,
        int estimatedMinutes,
        String whyThisTaskRationale,
        String promptPreviewLatex,
        DailyCoachTaskStatus status,
        Instant completedAt
) {
}
