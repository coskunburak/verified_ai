package com.verifiedai.exam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Phase 9 Exam & Assessment Platform feature flags (ADR-011).
 */
@Component
@ConfigurationProperties(prefix = "verifiedai.exam")
public class ExamProperties {

    private boolean readinessEnabled = false;

    public boolean isReadinessEnabled() {
        return readinessEnabled;
    }

    public void setReadinessEnabled(boolean readinessEnabled) {
        this.readinessEnabled = readinessEnabled;
    }
}
