package com.verifiedai.studyplan.session;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties governing durable study session orchestration,
 * inactivity expiration, and server-authoritative duration clamping (Sprint 8.7).
 */
@ConfigurationProperties("verifiedai.studyplan")
public record StudySessionOrchestrationProperties(
    Boolean sessionOrchestrationEnabled,
    int sessionInactivityTtlHours,
    int sessionClockSkewToleranceSeconds,
    int sessionMaxActiveDurationSeconds
) {

    public StudySessionOrchestrationProperties {
        if (sessionOrchestrationEnabled == null) {
            sessionOrchestrationEnabled = Boolean.TRUE;
        }
        if (sessionInactivityTtlHours <= 0) {
            sessionInactivityTtlHours = 24;
        }
        if (sessionClockSkewToleranceSeconds < 0) {
            sessionClockSkewToleranceSeconds = 30;
        }
        if (sessionMaxActiveDurationSeconds <= 0) {
            sessionMaxActiveDurationSeconds = 14400;
        }
    }

    public static StudySessionOrchestrationProperties defaults() {
        return new StudySessionOrchestrationProperties(Boolean.TRUE, 24, 30, 14400);
    }

    public StudySessionOrchestrationProperties withEnabled(boolean enabled) {
        return new StudySessionOrchestrationProperties(
            enabled,
            sessionInactivityTtlHours,
            sessionClockSkewToleranceSeconds,
            sessionMaxActiveDurationSeconds
        );
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(sessionOrchestrationEnabled);
    }
}
