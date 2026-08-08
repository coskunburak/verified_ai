package com.verifiedai.problem.infrastructure.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(ProblemAssetStorageProperties.class)
class ProblemAssetStorageConfiguration {
}
