package com.verifiedai.notification.application;

import com.verifiedai.notification.domain.NotificationCategory;
import com.verifiedai.notification.domain.NotificationDeliveryStatus;
import com.verifiedai.notification.domain.NotificationSuppressionReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Low-cardinality Micrometer metrics for notification scheduling, dispatch, preferences, and device lifecycle.
 */
@Component
public class NotificationMetrics {

    private final MeterRegistry meterRegistry;

    public NotificationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
    }

    public void recordScheduled(NotificationCategory category, NotificationDeliveryStatus status) {
        Counter.builder("verifiedai.notification.scheduled.total")
            .tag("category", category.name())
            .tag("status", status.name())
            .register(meterRegistry)
            .increment();
    }

    public void recordSuppressed(NotificationCategory category, NotificationSuppressionReason reason) {
        Counter.builder("verifiedai.notification.suppressed.total")
            .tag("category", category.name())
            .tag("reason", reason.name())
            .register(meterRegistry)
            .increment();
    }

    public void recordDispatch(NotificationCategory category, String outcome, Duration duration) {
        Counter.builder("verifiedai.notification.dispatch.total")
            .tag("category", category.name())
            .tag("outcome", outcome)
            .register(meterRegistry)
            .increment();
        Timer.builder("verifiedai.notification.dispatch.duration")
            .tag("outcome", outcome)
            .register(meterRegistry)
            .record(duration);
    }

    public void recordPreferenceUpdate(String outcome) {
        Counter.builder("verifiedai.notification.preference.updated.total")
            .tag("outcome", outcome)
            .register(meterRegistry)
            .increment();
    }

    public void recordDeviceLifecycle(String action) {
        Counter.builder("verifiedai.notification.device.lifecycle.total")
            .tag("action", action)
            .register(meterRegistry)
            .increment();
    }

    public void recordLifecycleEvaluated(String category, String decision) {
        Counter.builder("verifiedai.notification.lifecycle.evaluated.total")
            .tag("category", category)
            .tag("decision", decision)
            .register(meterRegistry)
            .increment();
    }

    public void recordLifecycleCancelled(String category, String reason) {
        Counter.builder("verifiedai.notification.lifecycle.cancelled.total")
            .tag("category", category)
            .tag("reason", reason)
            .register(meterRegistry)
            .increment();
    }

    public void recordLifecycleScanDuration(String module, Duration duration) {
        String boundedModule = switch (module) {
            case "studyplan", "exam", "billing" -> module;
            default -> "unknown";
        };
        Timer.builder("verifiedai.notification.lifecycle.scan.duration")
            .tag("module", boundedModule)
            .register(meterRegistry)
            .record(duration);
    }
}
