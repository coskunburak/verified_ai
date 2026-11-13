package com.verifiedai.studyplan.coach.dto;

import com.verifiedai.studyplan.coach.DailyCoachMode;
import jakarta.validation.constraints.NotNull;

public record DailyCoachPreferencesRequest(
        @NotNull DailyCoachMode preferredMode,
        @NotNull Integer budgetMinutes
) {
}
