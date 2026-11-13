package com.verifiedai.studyplan.coach.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DailyCoachTaskCompleteRequest(
        @NotNull UUID taskId,
        boolean verifiedIndependentSolve,
        @Min(0) int durationSeconds
) {
}
