package com.verifiedai.featureflags.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Low-cardinality Micrometer metrics for feature flag evaluations, kill switches,
 * experiment assignments, and administrative mutations.
 */
@Component
public class FeatureFlagMetrics {

    private final MeterRegistry meterRegistry;

    public FeatureFlagMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
    }

    public void recordFlagEvaluation(String category, String outcome) {
        Counter.builder("verifiedai.featureflags.evaluated.total")
            .tag("category", safeTag(category))
            .tag("outcome", safeTag(outcome))
            .register(meterRegistry)
            .increment();
    }

    public void recordKillSwitchToggle(boolean active) {
        Counter.builder("verifiedai.featureflags.kill_switch.toggled.total")
            .tag("active", Boolean.toString(active))
            .register(meterRegistry)
            .increment();
    }

    public void recordExperimentAssignment(String status, String reason) {
        Counter.builder("verifiedai.featureflags.experiment.assigned.total")
            .tag("status", safeTag(status))
            .tag("reason", safeTag(reason))
            .register(meterRegistry)
            .increment();
    }

    public void recordAdminMutation(String action, String outcome) {
        Counter.builder("verifiedai.featureflags.admin.mutation.total")
            .tag("action", safeTag(action))
            .tag("outcome", safeTag(outcome))
            .register(meterRegistry)
            .increment();
    }

    private static String safeTag(String value) {
        return (value == null || value.isBlank()) ? "UNKNOWN" : value;
    }
}
