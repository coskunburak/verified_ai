package com.verifiedai.problem.infrastructure.classification;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(
    ProblemClassifierProperties.class
)
class ProblemClassifierConfiguration {
}
