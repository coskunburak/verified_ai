package com.verifiedai.studyplan.session;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StudySessionCompleteResponse(
    @JsonProperty("schema_version") String schemaVersion,
    @JsonProperty("session") StudySessionStateDto session,
    @JsonProperty("accepted_active_duration_seconds") int acceptedActiveDurationSeconds,
    @JsonProperty("duration_clamped") boolean durationClamped,
    @JsonProperty("idempotent_replay") boolean idempotentReplay,
    @JsonProperty("supportive_summary_key") String supportiveSummaryKey,
    @JsonProperty("training_eligible") boolean trainingEligible
) {
}
