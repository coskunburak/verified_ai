package com.verifiedai.studyplan.rebalance;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Immutable Spring Modulith domain event published when a study plan rebalance completes.
 */
public record RebalanceEvent(
        UUID eventId,
        UUID userId,
        LocalDate recoveryDate,
        RebalanceTriggerType triggerType,
        int missedDaysCount,
        int daysUntilExam,
        int previousDailyBudgetMinutes,
        int rebalancedDailyBudgetMinutes,
        int prunedTasksCount,
        int rescheduledReviewsCount,
        int activeTodayTasksCount,
        boolean miniModeActivated,
        PruneReasonCode pruneReasonCode,
        Instant occurredAt
) {
}
