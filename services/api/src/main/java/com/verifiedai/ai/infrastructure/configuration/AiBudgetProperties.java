package com.verifiedai.ai.infrastructure.configuration;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.ai.budget")
public record AiBudgetProperties(String policyVersion, long attemptMicros, long requestMicros,
    long sessionMicros, long userDailyMicros, long userMonthlyMicros,
    long providerMonthlyMicros, long globalMonthlyMicros, Map<String, Long> tierRequestMicros) {
    public AiBudgetProperties {
        policyVersion = policyVersion == null ? "paid-disabled-v1" : policyVersion;
        tierRequestMicros = tierRequestMicros == null ? Map.of("FREE", 0L, "PRO", 0L, "PRO_PLUS", 0L) : Map.copyOf(tierRequestMicros);
        if (policyVersion.isBlank() || policyVersion.length() > 128
            || attemptMicros < 0 || requestMicros < 0 || sessionMicros < 0 || userDailyMicros < 0
            || userMonthlyMicros < 0 || providerMonthlyMicros < 0 || globalMonthlyMicros < 0
            || tierRequestMicros.values().stream().anyMatch(v -> v < 0)) {
            throw new IllegalArgumentException("Invalid AI spending policy");
        }
    }
}
