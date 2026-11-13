package com.verifiedai.studyplan.rebalance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.verifiedai.studyplan.rebalance.PruneReasonCode;
import com.verifiedai.studyplan.rebalance.RebalanceTriggerType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record StudyPlanRebalanceResponse(
        @JsonProperty("schema_version") String schemaVersion,
        @JsonProperty("rebalance_event_id") UUID rebalanceEventId,
        @JsonProperty("user_id") UUID userId,
        @JsonProperty("recovery_date") LocalDate recoveryDate,
        @JsonProperty("trigger_type") RebalanceTriggerType triggerType,
        @JsonProperty("missed_days_count") int missedDaysCount,
        @JsonProperty("days_until_exam") int daysUntilExam,
        @JsonProperty("previous_daily_budget_minutes") int previousDailyBudgetMinutes,
        @JsonProperty("rebalanced_daily_budget_minutes") int rebalancedDailyBudgetMinutes,
        @JsonProperty("pruned_tasks_count") int prunedTasksCount,
        @JsonProperty("rescheduled_reviews_count") int rescheduledReviewsCount,
        @JsonProperty("active_today_tasks_count") int activeTodayTasksCount,
        @JsonProperty("mini_mode_activated") boolean miniModeActivated,
        @JsonProperty("prune_reason_code") PruneReasonCode pruneReasonCode,
        @JsonProperty("pruned_concept_ids") List<String> prunedConceptIds,
        @JsonProperty("welcome_message_key") String welcomeMessageKey,
        @JsonProperty("idempotent_replay") boolean idempotentReplay,
        @JsonProperty("created_at") Instant createdAt
) {
}
