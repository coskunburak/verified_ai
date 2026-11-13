package com.verifiedai.studyplan.session;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StudySessionResumeSnapshot(
    @JsonProperty("last_step_index") @Min(0) @Max(50) int lastStepIndex,
    @JsonProperty("draft_step_notes") @NotNull @Size(max = 2000) String draftStepNotes,
    @JsonProperty("offline_queued_count") @Min(0) @Max(100) int offlineQueuedCount,
    @JsonProperty("active_sub_step") @Size(max = 64) String activeSubStep,
    @JsonProperty("ui_scroll_anchor") @Size(max = 128) String uiScrollAnchor,
    @JsonProperty("mistake_code") @Size(max = 64) String mistakeCode
) {
    public static StudySessionResumeSnapshot empty() {
        return new StudySessionResumeSnapshot(0, "", 0, null, null, null);
    }

    public StudySessionResumeSnapshot withOfflineQueuedCount(int count) {
        return new StudySessionResumeSnapshot(
            lastStepIndex,
            draftStepNotes,
            Math.max(0, Math.min(100, count)),
            activeSubStep,
            uiScrollAnchor,
            mistakeCode
        );
    }
}
