package com.verifiedai.problem.infrastructure.storage;

import com.verifiedai.problem.infrastructure.preprocessing.ProblemAssetPreprocessingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({ ProblemAssetStorageProperties.class, ProblemAssetPreprocessingProperties.class })
class ProblemAssetStorageConfiguration {
}
