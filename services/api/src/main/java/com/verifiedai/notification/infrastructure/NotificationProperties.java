package com.verifiedai.notification.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Fail-closed configuration properties for Sprint 10.4 and Sprint 10.5 notification and lifecycle messaging.
 */
@Validated
@ConfigurationProperties(prefix = "verifiedai.notification")
public record NotificationProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("false") boolean dispatchWorkerEnabled,
    @DefaultValue("false") boolean apnsLiveDeliveryEnabled,
    @DefaultValue("50") int dispatchBatchSize,
    @DefaultValue("15000") long dispatchPollIntervalMs,
    @DefaultValue("60") int dispatchLockTtlSeconds,
    @DefaultValue("2") int defaultDailyCap,
    @DefaultValue("5") int maxDailyCap,
    @DefaultValue("3") int maxDeliveryAttempts,
    @DefaultValue("60") int retryBaseDelaySeconds,
    @DefaultValue("10") int maxActiveDevicesPerUser,
    @DefaultValue("false") boolean lifecycleCampaignsEnabled,
    @DefaultValue("false") boolean lifecycleSchedulerEnabled,
    @DefaultValue("100") int lifecycleScanBatchSize,
    @DefaultValue("300000") long lifecycleScanIntervalMs,
    @DefaultValue("20") int studyPlanCooldownHours,
    @DefaultValue("24") int spacedReviewCooldownHours,
    @DefaultValue("24") int examDeadlineCooldownHours,
    @DefaultValue("48") int trialReminderLeadHours
) {
    @ConstructorBinding
    public NotificationProperties {
        if (apnsLiveDeliveryEnabled) {
            throw new IllegalStateException(
                "Live APNs gateway is not provisioned in Sprint 10.4; keep apns-live-delivery-enabled=false"
            );
        }
        if (dispatchBatchSize < 1 || dispatchBatchSize > 200) {
            throw new IllegalStateException("verifiedai.notification.dispatch-batch-size must be between 1 and 200");
        }
        if (dispatchPollIntervalMs < 1_000L || dispatchPollIntervalMs > 3_600_000L) {
            throw new IllegalStateException(
                "verifiedai.notification.dispatch-poll-interval-ms must be between 1000 and 3600000"
            );
        }
        if (dispatchLockTtlSeconds < 15 || dispatchLockTtlSeconds > 600) {
            throw new IllegalStateException("verifiedai.notification.dispatch-lock-ttl-seconds must be between 15 and 600");
        }
        if (defaultDailyCap < 0 || defaultDailyCap > 5) {
            throw new IllegalStateException("verifiedai.notification.default-daily-cap must be between 0 and 5");
        }
        if (maxDailyCap != 5) {
            throw new IllegalStateException("verifiedai.notification.max-daily-cap must equal 5");
        }
        if (defaultDailyCap > maxDailyCap) {
            throw new IllegalStateException("verifiedai.notification.default-daily-cap must not exceed max-daily-cap");
        }
        if (maxDeliveryAttempts < 1 || maxDeliveryAttempts > 5) {
            throw new IllegalStateException("verifiedai.notification.max-delivery-attempts must be between 1 and 5");
        }
        if (retryBaseDelaySeconds < 5 || retryBaseDelaySeconds > 3600) {
            throw new IllegalStateException("verifiedai.notification.retry-base-delay-seconds must be between 5 and 3600");
        }
        if (maxActiveDevicesPerUser < 1 || maxActiveDevicesPerUser > 20) {
            throw new IllegalStateException("verifiedai.notification.max-active-devices-per-user must be between 1 and 20");
        }
        if (lifecycleScanBatchSize < 1 || lifecycleScanBatchSize > 500) {
            throw new IllegalStateException(
                "verifiedai.notification.lifecycle-scan-batch-size must be between 1 and 500"
            );
        }
        if (lifecycleScanIntervalMs < 10_000L) {
            throw new IllegalStateException(
                "verifiedai.notification.lifecycle-scan-interval-ms must be at least 10000"
            );
        }
        validateCooldownHours("study-plan-cooldown-hours", studyPlanCooldownHours);
        validateCooldownHours("spaced-review-cooldown-hours", spacedReviewCooldownHours);
        validateCooldownHours("exam-deadline-cooldown-hours", examDeadlineCooldownHours);
        if (trialReminderLeadHours < 12 || trialReminderLeadHours > 168) {
            throw new IllegalStateException(
                "verifiedai.notification.trial-reminder-lead-hours must be between 12 and 168"
            );
        }
    }

    /**
     * Backward-compatible Sprint 10.4 11-argument constructor preserving existing unit tests.
     */
    public NotificationProperties(
        boolean enabled,
        boolean dispatchWorkerEnabled,
        boolean apnsLiveDeliveryEnabled,
        int dispatchBatchSize,
        long dispatchPollIntervalMs,
        int dispatchLockTtlSeconds,
        int defaultDailyCap,
        int maxDailyCap,
        int maxDeliveryAttempts,
        int retryBaseDelaySeconds,
        int maxActiveDevicesPerUser
    ) {
        this(
            enabled,
            dispatchWorkerEnabled,
            apnsLiveDeliveryEnabled,
            dispatchBatchSize,
            dispatchPollIntervalMs,
            dispatchLockTtlSeconds,
            defaultDailyCap,
            maxDailyCap,
            maxDeliveryAttempts,
            retryBaseDelaySeconds,
            maxActiveDevicesPerUser,
            false,
            false,
            100,
            300_000L,
            20,
            24,
            24,
            48
        );
    }

    private static void validateCooldownHours(String propertyName, int hours) {
        if (hours < 1 || hours > 168) {
            throw new IllegalStateException("verifiedai.notification." + propertyName + " must be between 1 and 168");
        }
    }

    public static NotificationProperties defaults() {
        return new NotificationProperties(
            false, false, false, 50, 15_000L, 60, 2, 5, 3, 60, 10,
            false, false, 100, 300_000L, 20, 24, 24, 48
        );
    }

    public static NotificationProperties enabledDefaults() {
        return new NotificationProperties(
            true, false, false, 50, 15_000L, 60, 2, 5, 3, 60, 10,
            false, false, 100, 300_000L, 20, 24, 24, 48
        );
    }

    public static NotificationProperties lifecycleEnabledDefaults() {
        return new NotificationProperties(
            true, false, false, 50, 15_000L, 60, 2, 5, 3, 60, 10,
            true, false, 100, 300_000L, 20, 24, 24, 48
        );
    }
}
