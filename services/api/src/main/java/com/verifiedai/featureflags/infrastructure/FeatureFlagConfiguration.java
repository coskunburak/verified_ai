package com.verifiedai.featureflags.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FeatureFlagProperties.class)
class FeatureFlagConfiguration {
}
