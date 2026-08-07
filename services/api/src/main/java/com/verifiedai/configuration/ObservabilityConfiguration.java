package com.verifiedai.configuration;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
class ObservabilityConfiguration {

    @Bean
    MeterRegistryCustomizer<MeterRegistry> platformCommonTags(Environment environment) {
        String activeEnvironment = environment.getActiveProfiles().length == 0
            ? "default"
            : String.join(",", environment.getActiveProfiles());
        return registry -> registry.config().commonTags(
            "service", "verified-ai-api",
            "environment", activeEnvironment
        );
    }
}
