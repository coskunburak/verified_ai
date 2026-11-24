package com.verifiedai.admin.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Low-cardinality Micrometer counters for Sprint 10.8 admin and support operations.
 * Never attaches user IDs, session IDs, object keys, or student content as metric tags.
 */
@Component
public class AdminSupportMetrics {

    private final MeterRegistry meterRegistry;

    public AdminSupportMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordQuery(String kind) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder("verifiedai.admin.query.total")
            .tag("kind", kind)
            .register(meterRegistry)
            .increment();
    }

    public void recordRemediation(String action, String outcome) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder("verifiedai.admin.remediation.total")
            .tag("action", action)
            .tag("outcome", outcome)
            .register(meterRegistry)
            .increment();
    }

    public void recordRawContent(String action) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder("verifiedai.admin.raw_content.total")
            .tag("action", action)
            .register(meterRegistry)
            .increment();
    }

    public void recordWrongAnswer(String status) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder("verifiedai.admin.wrong_answer.total")
            .tag("status", status)
            .register(meterRegistry)
            .increment();
    }
}
