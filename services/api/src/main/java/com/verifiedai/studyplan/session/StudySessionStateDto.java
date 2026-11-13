package com.verifiedai.studyplan.session;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StudySessionStateDto(
    @JsonProperty("schema_version") String schemaVersion,
    @JsonProperty("session_id") UUID sessionId,
    @JsonProperty("user_id") UUID userId,
    @JsonProperty("coach_session_id") UUID coachSessionId,
    @JsonProperty("source_context") StudySessionSourceContext sourceContext,
    @JsonProperty("topic_code") String topicCode,
    @JsonProperty("locale") String locale,
    @JsonProperty("state") StudySessionState state,
    @JsonProperty("total_steps") int totalSteps,
    @JsonProperty("current_step_index") int currentStepIndex,
    @JsonProperty("completed_steps_count") int completedStepsCount,
    @JsonProperty("last_sequence_number") long lastSequenceNumber,
    @JsonProperty("active_duration_seconds") int activeDurationSeconds,
    @JsonProperty("paused_duration_seconds") int pausedDurationSeconds,
    @JsonProperty("client_instance_id") String clientInstanceId,
    @JsonProperty("interruption_reason") StudySessionInterruptionReason interruptionReason,
    @JsonProperty("step_blueprint") List<StudySessionStepBlueprintItem> stepBlueprint,
    @JsonProperty("resume_snapshot") StudySessionResumeSnapshot resumeSnapshot,
    @JsonProperty("started_at") @JsonFormat(shape = JsonFormat.Shape.STRING) Instant startedAt,
    @JsonProperty("last_checkpoint_at") @JsonFormat(shape = JsonFormat.Shape.STRING) Instant lastCheckpointAt,
    @JsonProperty("paused_at") @JsonFormat(shape = JsonFormat.Shape.STRING) Instant pausedAt,
    @JsonProperty("resumed_at") @JsonFormat(shape = JsonFormat.Shape.STRING) Instant resumedAt,
    @JsonProperty("completed_at") @JsonFormat(shape = JsonFormat.Shape.STRING) Instant completedAt,
    @JsonProperty("expires_at") @JsonFormat(shape = JsonFormat.Shape.STRING) Instant expiresAt,
    @JsonProperty("training_eligible") boolean trainingEligible
) {
}
