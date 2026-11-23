package com.verifiedai.analytics.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Fail-closed configuration properties for Sprint 10.6 analytics event catalog and product metrics.
 */
@Validated
@ConfigurationProperties(prefix = "verifiedai.analytics")
public record AnalyticsProperties(
    @DefaultValue("true") boolean weeklyDigestEnabled,
    @DefaultValue("false") boolean eventCatalogEnabled,
    @DefaultValue("false") boolean productMetricsEnabled,
    @DefaultValue("50") int maxBatchSize,
    @DefaultValue("2048") int maxPropertiesBytes,
    @DefaultValue("16") int maxPropertyCount,
    @DefaultValue("90") int maxMetricsWindowDays,
    @DefaultValue("365") int retentionDays
) {
    @ConstructorBinding
    public AnalyticsProperties {
        if (maxBatchSize < 1 || maxBatchSize > 100) {
            throw new IllegalStateException("verifiedai.analytics.max-batch-size must be between 1 and 100");
        }
        if (maxPropertiesBytes < 256 || maxPropertiesBytes > 4096) {
            throw new IllegalStateException("verifiedai.analytics.max-properties-bytes must be between 256 and 4096");
        }
        if (maxPropertyCount < 1 || maxPropertyCount > 32) {
            throw new IllegalStateException("verifiedai.analytics.max-property-count must be between 1 and 32");
        }
        if (maxMetricsWindowDays < 1 || maxMetricsWindowDays > 90) {
            throw new IllegalStateException("verifiedai.analytics.max-metrics-window-days must be between 1 and 90");
        }
        if (retentionDays < 30 || retentionDays > 730) {
            throw new IllegalStateException("verifiedai.analytics.retention-days must be between 30 and 730");
        }
    }

    public static AnalyticsProperties defaults() {
        return new AnalyticsProperties(true, false, false, 50, 2048, 16, 90, 365);
    }

    public static AnalyticsProperties enabledDefaults() {
        return new AnalyticsProperties(true, true, true, 50, 2048, 16, 90, 365);
    }
}
