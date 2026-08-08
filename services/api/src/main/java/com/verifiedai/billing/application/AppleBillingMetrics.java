package com.verifiedai.billing.application;

import com.verifiedai.billing.domain.model.AppStoreNotificationProcessingStatus;
import com.verifiedai.billing.domain.model.AppStoreSubscriptionStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
class AppleBillingMetrics {
    private final MeterRegistry meterRegistry;

    AppleBillingMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    void purchaseEvidenceAccepted(AppStoreSubscriptionStatus status, boolean duplicate) {
        Counter.builder("billing.apple.purchase_evidence.accepted.total")
            .tag("subscription_status", status.name())
            .tag("duplicate", Boolean.toString(duplicate))
            .register(meterRegistry)
            .increment();
    }

    void purchaseEvidenceRejected(String reason) {
        Counter.builder("billing.apple.purchase_evidence.rejected.total")
            .tag("reason", reason)
            .register(meterRegistry)
            .increment();
    }

    void notificationProcessed(String notificationType, AppStoreNotificationProcessingStatus status) {
        Counter.builder("billing.apple.notification.processed.total")
            .tag("notification_type", notificationType == null ? "UNKNOWN" : notificationType)
            .tag("processing_status", status.name())
            .register(meterRegistry)
            .increment();
    }
}
