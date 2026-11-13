package com.verifiedai.studyplan.session;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record StudySessionCheckpointRequest(
    @JsonProperty("sequence_number") @Min(1) long sequenceNumber,
    @JsonProperty("step_index") @Min(0) @Max(50) int stepIndex,
    @JsonProperty("step_type") @NotNull StudySessionStepType stepType,
    @JsonProperty("step_status") @NotNull StudySessionStepStatus stepStatus,
    @JsonProperty("reported_active_duration_seconds") @Min(0) @Max(14400) int reportedActiveDurationSeconds,
    @JsonProperty("offline_queued") boolean offlineQueued,
    @JsonProperty("client_recorded_at") @NotNull Instant clientRecordedAt,
    @JsonProperty("idempotency_key") @NotBlank @Size(min = 16, max = 128) @Pattern(regexp = "^[A-Za-z0-9_.:-]+$") String idempotencyKey,
    @JsonProperty("interruption_reason") StudySessionInterruptionReason interruptionReason,
    @JsonProperty("client_instance_id") @Size(min = 4, max = 128) String clientInstanceId,
    @JsonProperty("resume_snapshot") @NotNull @Valid StudySessionResumeSnapshot resumeSnapshot
) {
}
