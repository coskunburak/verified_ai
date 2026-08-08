package com.verifiedai.identity.application;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
final class AccountPrivacyMetrics {
    private final MeterRegistry meterRegistry;

    AccountPrivacyMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    void exportRequested() {
        meterRegistry.counter("privacy.export.request.total").increment();
    }

    void exportDownloaded() {
        meterRegistry.counter("privacy.export.download.total").increment();
    }

    void deletionRequested() {
        meterRegistry.counter("privacy.deletion.request.total").increment();
    }

    void deletionCompleted() {
        meterRegistry.counter("privacy.deletion.success.total").increment();
    }
}
