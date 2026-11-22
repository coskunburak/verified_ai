package com.verifiedai.notification.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Fail-closed configuration properties for Sprint 10.4 notification infrastructure.
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
    @DefaultValue("10") int maxActiveDevicesPerUser
) {
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
    }

    public static NotificationProperties defaults() {
        return new NotificationProperties(false, false, false, 50, 15_000L, 60, 2, 5, 3, 60, 10);
    }

    public static NotificationProperties enabledDefaults() {
        return new NotificationProperties(true, false, false, 50, 15_000L, 60, 2, 5, 3, 60, 10);
    }
}
