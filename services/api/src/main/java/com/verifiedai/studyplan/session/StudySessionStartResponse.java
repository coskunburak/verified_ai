package com.verifiedai.studyplan.session;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record StudySessionStartResponse(
    @JsonProperty("schema_version") String schemaVersion,
    @JsonProperty("session") StudySessionStateDto session,
    @JsonProperty("auto_paused_prior_session_id") UUID autoPausedPriorSessionId,
    @JsonProperty("expired_prior_session_id") UUID expiredPriorSessionId,
    @JsonProperty("training_eligible") boolean trainingEligible
) {
}
