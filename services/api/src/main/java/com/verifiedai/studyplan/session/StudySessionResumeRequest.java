package com.verifiedai.studyplan.session;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public record StudySessionResumeRequest(
    @JsonProperty("client_instance_id") @Size(min = 4, max = 128) String clientInstanceId,
    @JsonProperty("resume_snapshot") @Valid StudySessionResumeSnapshot resumeSnapshot
) {
}
