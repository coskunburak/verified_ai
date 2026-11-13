package com.verifiedai.studyplan.session;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StudySessionCheckpointResponse(
    @JsonProperty("schema_version") String schemaVersion,
    @JsonProperty("session") StudySessionStateDto session,
    @JsonProperty("accepted_sequence_number") long acceptedSequenceNumber,
    @JsonProperty("accepted_active_duration_seconds") int acceptedActiveDurationSeconds,
    @JsonProperty("duration_clamped") boolean durationClamped,
    @JsonProperty("idempotent_replay") boolean idempotentReplay,
    @JsonProperty("stale_checkpoint_ignored") boolean staleCheckpointIgnored,
    @JsonProperty("training_eligible") boolean trainingEligible
) {
}
