package com.verifiedai.identity.application;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
final class AuthMetrics {
    private final MeterRegistry meterRegistry;

    AuthMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    void loginAttempt() {
        meterRegistry.counter("auth.login.attempt.total").increment();
    }

    void loginSuccess() {
        meterRegistry.counter("auth.login.success.total").increment();
    }

    void loginFailure() {
        meterRegistry.counter("auth.login.failure.total").increment();
    }

    void refreshSuccess() {
        meterRegistry.counter("auth.refresh.success.total").increment();
    }

    void refreshFailure() {
        meterRegistry.counter("auth.refresh.failure.total").increment();
    }

    void refreshReuseDetected() {
        meterRegistry.counter("auth.refresh.reuse_detected.total").increment();
    }

    void sessionRevoked() {
        meterRegistry.counter("auth.session.revoked.total").increment();
    }
}
