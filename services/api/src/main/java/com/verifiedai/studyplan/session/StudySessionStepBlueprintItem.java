package com.verifiedai.studyplan.session;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StudySessionStepBlueprintItem(
    @JsonProperty("step_index") @Min(0) @Max(49) int stepIndex,
    @JsonProperty("step_type") @NotNull StudySessionStepType stepType,
    @JsonProperty("step_title") @NotBlank @Size(min = 1, max = 160) String stepTitle,
    @JsonProperty("estimated_seconds") @Min(10) @Max(3600) int estimatedSeconds,
    @JsonProperty("status") @NotNull StudySessionBlueprintStatus status,
    @JsonProperty("target_skill_code") @Size(max = 64) String targetSkillCode,
    @JsonProperty("problem_id") @Size(max = 64) String problemId
) {
    public StudySessionStepBlueprintItem withStatus(StudySessionBlueprintStatus newStatus) {
        return new StudySessionStepBlueprintItem(
            stepIndex,
            stepType,
            stepTitle,
            estimatedSeconds,
            newStatus,
            targetSkillCode,
            problemId
        );
    }
}
