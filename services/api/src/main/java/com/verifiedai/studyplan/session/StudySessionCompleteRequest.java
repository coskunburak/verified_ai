package com.verifiedai.studyplan.session;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StudySessionCompleteRequest(
    @JsonProperty("final_step_index") @Min(0) @Max(50) int finalStepIndex,
    @JsonProperty("reported_active_duration_seconds") @Min(0) @Max(14400) int reportedActiveDurationSeconds,
    @JsonProperty("idempotency_key") @NotBlank @Size(min = 16, max = 128) @Pattern(regexp = "^[A-Za-z0-9_.:-]+$") String idempotencyKey,
    @JsonProperty("completion_notes") @Size(max = 500) String completionNotes,
    @JsonProperty("resume_snapshot") @Valid StudySessionResumeSnapshot resumeSnapshot
) {
}
