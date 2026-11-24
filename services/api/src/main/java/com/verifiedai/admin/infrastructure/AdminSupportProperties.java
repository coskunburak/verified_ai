package com.verifiedai.admin.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Fail-closed configuration properties for Sprint 10.8 Admin and Support Console Foundations.
 */
@ConfigurationProperties(prefix = "verifiedai.admin")
public record AdminSupportProperties(
    boolean consoleEnabled,
    boolean remediationEnabled,
    boolean rawContentAccessEnabled,
    int maxLookupLimit,
    int maxTraceRecords,
    int rawContentGrantTtlMinutes,
    int auditHistoryLimit
) {

    public AdminSupportProperties {
        if (maxLookupLimit < 1 || maxLookupLimit > 100) {
            throw new IllegalArgumentException("verifiedai.admin.max-lookup-limit must be between 1 and 100");
        }
        if (maxTraceRecords < 1 || maxTraceRecords > 100) {
            throw new IllegalArgumentException("verifiedai.admin.max-trace-records must be between 1 and 100");
        }
        if (rawContentGrantTtlMinutes < 5 || rawContentGrantTtlMinutes > 60) {
            throw new IllegalArgumentException(
                "verifiedai.admin.raw-content-grant-ttl-minutes must be between 5 and 60"
            );
        }
        if (auditHistoryLimit < 1 || auditHistoryLimit > 200) {
            throw new IllegalArgumentException("verifiedai.admin.audit-history-limit must be between 1 and 200");
        }
    }

    public static AdminSupportProperties defaults() {
        return new AdminSupportProperties(false, false, false, 50, 50, 15, 50);
    }
}
