package com.verifiedai.profile.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
class ProfileMetrics {
    private final Counter loadSuccess;
    private final Counter saveSuccess;
    private final Counter saveFailure;
    private final Counter onboardingCompleted;

    ProfileMetrics(MeterRegistry meterRegistry) {
        this.loadSuccess = Counter.builder("profile.load.success.total").register(meterRegistry);
        this.saveSuccess = Counter.builder("profile.save.success.total").register(meterRegistry);
        this.saveFailure = Counter.builder("profile.save.failure.total").register(meterRegistry);
        this.onboardingCompleted = Counter.builder("onboarding.completed.total").register(meterRegistry);
    }

    void loadSuccess() {
        loadSuccess.increment();
    }

    void saveSuccess() {
        saveSuccess.increment();
    }

    void saveFailure() {
        saveFailure.increment();
    }

    void onboardingCompleted() {
        onboardingCompleted.increment();
    }
}
