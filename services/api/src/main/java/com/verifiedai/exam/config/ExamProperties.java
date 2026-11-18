package com.verifiedai.exam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Phase 9 Exam & Assessment Platform feature flags (ADR-011).
 */
@Component
@ConfigurationProperties(prefix = "verifiedai.exam")
public class ExamProperties {

    private boolean analyticsEnabled = false;
    private boolean preExamModeEnabled = false;

    public boolean isAnalyticsEnabled() { return analyticsEnabled; }
    public void setAnalyticsEnabled(boolean enabled) { this.analyticsEnabled = enabled; }

    public boolean isPreExamModeEnabled() { return preExamModeEnabled; }
    public void setPreExamModeEnabled(boolean enabled) { preExamModeEnabled = enabled; }

    private boolean readinessEnabled = false;

    public boolean isReadinessEnabled() {
        return readinessEnabled;
    }

    public void setReadinessEnabled(boolean readinessEnabled) {
        this.readinessEnabled = readinessEnabled;
    }
    private boolean attributionEnabled = false;
    private boolean netGainSimulatorEnabled = false;

    public boolean isAttributionEnabled() { return attributionEnabled; }
    public void setAttributionEnabled(boolean enabled) { attributionEnabled = enabled; }
    public boolean isNetGainSimulatorEnabled() { return netGainSimulatorEnabled; }
    public void setNetGainSimulatorEnabled(boolean enabled) { netGainSimulatorEnabled = enabled; }
}
