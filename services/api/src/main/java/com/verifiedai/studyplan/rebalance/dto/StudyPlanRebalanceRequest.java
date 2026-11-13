package com.verifiedai.studyplan.rebalance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.verifiedai.studyplan.rebalance.RebalanceTriggerType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record StudyPlanRebalanceRequest(
        @NotNull @JsonProperty("trigger_type") RebalanceTriggerType triggerType,
        @Min(0) @Max(365) @JsonProperty("missed_days_override") Integer missedDaysOverride,
        @Min(1) @Max(730) @JsonProperty("days_until_exam_override") Integer daysUntilExamOverride,
        @JsonProperty("prefer_mini_mode") Boolean preferMiniMode,
        @Size(min = 1, max = 128) @JsonProperty("idempotency_key") String idempotencyKey,
        @JsonProperty("as_of_date") LocalDate asOfDate
) {
}
