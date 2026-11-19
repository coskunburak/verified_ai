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
    private boolean integrityGuardEnabled = false;
    private String integrityActiveKeyId = "exam-k1";
    private String integrityMasterKey = "";
    private String integrityPreviousKeyId = "";
    private String integrityPreviousMasterKey = "";
    private long integrityMaxClockSkewMs = 30_000L;
    private int integrityAuditRetentionDays = 30;
    private String qualityGateEvidencePath = "";

    public boolean isAttributionEnabled() { return attributionEnabled; }
    public void setAttributionEnabled(boolean enabled) { attributionEnabled = enabled; }
    public boolean isNetGainSimulatorEnabled() { return netGainSimulatorEnabled; }
    public void setNetGainSimulatorEnabled(boolean enabled) { netGainSimulatorEnabled = enabled; }

    public boolean isIntegrityGuardEnabled() { return integrityGuardEnabled; }
    public void setIntegrityGuardEnabled(boolean integrityGuardEnabled) { this.integrityGuardEnabled = integrityGuardEnabled; }

    public String getIntegrityActiveKeyId() { return integrityActiveKeyId; }
    public void setIntegrityActiveKeyId(String integrityActiveKeyId) { this.integrityActiveKeyId = integrityActiveKeyId; }

    public String getIntegrityMasterKey() { return integrityMasterKey; }
    public void setIntegrityMasterKey(String integrityMasterKey) { this.integrityMasterKey = integrityMasterKey; }

    public String getIntegrityPreviousKeyId() { return integrityPreviousKeyId; }
    public void setIntegrityPreviousKeyId(String integrityPreviousKeyId) { this.integrityPreviousKeyId = integrityPreviousKeyId; }

    public String getIntegrityPreviousMasterKey() { return integrityPreviousMasterKey; }
    public void setIntegrityPreviousMasterKey(String integrityPreviousMasterKey) { this.integrityPreviousMasterKey = integrityPreviousMasterKey; }

    public long getIntegrityMaxClockSkewMs() { return integrityMaxClockSkewMs; }
    public void setIntegrityMaxClockSkewMs(long integrityMaxClockSkewMs) { this.integrityMaxClockSkewMs = integrityMaxClockSkewMs; }

    public int getIntegrityAuditRetentionDays() { return integrityAuditRetentionDays; }
    public void setIntegrityAuditRetentionDays(int integrityAuditRetentionDays) { this.integrityAuditRetentionDays = integrityAuditRetentionDays; }

    public String getQualityGateEvidencePath() { return qualityGateEvidencePath; }
    public void setQualityGateEvidencePath(String qualityGateEvidencePath) { this.qualityGateEvidencePath = qualityGateEvidencePath; }
}
