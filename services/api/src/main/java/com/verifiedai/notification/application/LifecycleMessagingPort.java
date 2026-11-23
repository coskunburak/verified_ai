package com.verifiedai.notification.application;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Public Spring Modulith SPI port for Sprint 10.5 lifecycle campaign orchestration, cooldowns, and obsolete cancellation.
 */
public interface LifecycleMessagingPort {

    LifecycleEvaluationResult evaluateAndSchedule(LifecycleTriggerCommand command);

    LifecycleEvaluationResult recordSkip(LifecycleSkipCommand command);

    LifecycleCancellationResult cancelPendingByIdempotencyKey(
        UUID userId,
        String sourceModule,
        String category,
        String intentType,
        String idempotencyKey,
        String reasonCode,
        String correlationId
    );

    LifecycleSummaryView getLifecycleSummary(UUID userId, int limit);

    default List<LifecycleCancellationResult> cancelPendingByCategoryExceptPrefix(
        UUID userId,
        String sourceModule,
        String category,
        String intentType,
        String retainIdempotencyKeyPrefix,
        String reasonCode,
        String correlationId
    ) {
        return List.of();
    }

    default void recordScanDuration(String module, Duration duration) {
    }

    default int trialReminderLeadHours() {
        return 48;
    }

    default int scanBatchSize() {
        return 100;
    }

    default String resolvePreferredLocale(UUID userId) {
        return "en";
    }
}
