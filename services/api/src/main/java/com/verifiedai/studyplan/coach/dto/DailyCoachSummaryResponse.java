package com.verifiedai.studyplan.coach.dto;

import com.verifiedai.studyplan.coach.DailyCoachMode;
import com.verifiedai.studyplan.coach.DailyCoachSessionStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DailyCoachSummaryResponse(
        UUID sessionId,
        UUID userId,
        LocalDate localDate,
        int budgetMinutes,
        DailyCoachSessionStatus status,
        boolean isDownshifted,
        boolean isColdStart,
        int completedTasksCount,
        int totalTasksCount,
        String briefingText,
        DailyCoachMode preferredMode,
        int streakDays,
        List<DailyCoachTaskDto> tasks,
        List<TrapRadarPreviewDto> activeTrapRadar,
        ColdStartDemoCardDto coldStartDemo
) {
}
