package com.verifiedai.featureflags.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Fail-closed configuration properties for Sprint 10.7 feature flags, remote configuration,
 * experimentation, and kill-switch governance.
 */
@Validated
@ConfigurationProperties(prefix = "verifiedai.featureflags")
public record FeatureFlagProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("false") boolean clientConfigEnabled,
    @DefaultValue("false") boolean adminMutationsEnabled,
    @DefaultValue("50") int maxClientFlags,
    @DefaultValue("50") int auditHistoryLimit,
    @DefaultValue("300") int clientEtagMaxAgeSeconds
) {
    @ConstructorBinding
    public FeatureFlagProperties {
        if (maxClientFlags < 1 || maxClientFlags > 100) {
            throw new IllegalStateException("verifiedai.featureflags.max-client-flags must be between 1 and 100");
        }
        if (auditHistoryLimit < 1 || auditHistoryLimit > 200) {
            throw new IllegalStateException("verifiedai.featureflags.audit-history-limit must be between 1 and 200");
        }
        if (clientEtagMaxAgeSeconds < 0 || clientEtagMaxAgeSeconds > 3600) {
            throw new IllegalStateException(
                "verifiedai.featureflags.client-etag-max-age-seconds must be between 0 and 3600"
            );
        }
    }

    public static FeatureFlagProperties defaults() {
        return new FeatureFlagProperties(false, false, false, 50, 50, 300);
    }

    public static FeatureFlagProperties enabledDefaults() {
        return new FeatureFlagProperties(true, true, true, 50, 50, 300);
    }
}
