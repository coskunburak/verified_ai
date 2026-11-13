package com.verifiedai.studyplan.session;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record StudySessionStartRequest(
    @JsonProperty("coach_session_id") UUID coachSessionId,
    @JsonProperty("source_context") @NotNull StudySessionSourceContext sourceContext,
    @JsonProperty("topic_code") @NotBlank @Size(min = 1, max = 128) String topicCode,
    @JsonProperty("locale") @NotBlank @Pattern(regexp = "^(tr-TR|en-US)$") String locale,
    @JsonProperty("total_steps") @Min(1) @Max(50) int totalSteps,
    @JsonProperty("client_instance_id") @NotBlank @Size(min = 4, max = 128) String clientInstanceId,
    @JsonProperty("step_blueprint") @NotNull @Size(min = 1, max = 50) List<@Valid StudySessionStepBlueprintItem> stepBlueprint,
    @JsonProperty("resume_snapshot") @Valid StudySessionResumeSnapshot resumeSnapshot
) {
}
