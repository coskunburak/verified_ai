package com.verifiedai.growth.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GrowthProperties.class)
public class GrowthConfiguration {
}
