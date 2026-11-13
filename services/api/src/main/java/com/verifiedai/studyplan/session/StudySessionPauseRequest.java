package com.verifiedai.studyplan.session;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record StudySessionPauseRequest(
    @JsonProperty("interruption_reason") StudySessionInterruptionReason interruptionReason,
    @JsonProperty("reported_active_duration_seconds") @Min(0) @Max(14400) Integer reportedActiveDurationSeconds,
    @JsonProperty("resume_snapshot") @Valid StudySessionResumeSnapshot resumeSnapshot
) {
}
