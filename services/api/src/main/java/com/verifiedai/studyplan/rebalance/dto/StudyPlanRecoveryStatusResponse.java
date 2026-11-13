package com.verifiedai.studyplan.rebalance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.verifiedai.studyplan.rebalance.PruneReasonCode;

import java.time.LocalDate;
import java.util.UUID;

public record StudyPlanRecoveryStatusResponse(
        @JsonProperty("schema_version") String schemaVersion,
        @JsonProperty("user_id") UUID userId,
        @JsonProperty("as_of_date") LocalDate asOfDate,
        @JsonProperty("recovery_needed") boolean recoveryNeeded,
        @JsonProperty("missed_days_count") int missedDaysCount,
        @JsonProperty("days_until_exam") int daysUntilExam,
        @JsonProperty("previous_daily_budget_minutes") int previousDailyBudgetMinutes,
        @JsonProperty("recommended_daily_budget_minutes") int recommendedDailyBudgetMinutes,
        @JsonProperty("mini_mode_recommended") boolean miniModeRecommended,
        @JsonProperty("active_today_tasks_count") int activeTodayTasksCount,
        @JsonProperty("pruned_tasks_count") int prunedTasksCount,
        @JsonProperty("rescheduled_reviews_count") int rescheduledReviewsCount,
        @JsonProperty("prune_reason_code") PruneReasonCode pruneReasonCode,
        @JsonProperty("welcome_message_key") String welcomeMessageKey,
        @JsonProperty("last_rebalance_event_id") UUID lastRebalanceEventId
) {
}
