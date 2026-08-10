package com.verifiedai.ai.infrastructure.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AiVisionRecognitionProperties.class, AiProblemParserProperties.class, AiProblemClassifierProperties.class})
class AiInfrastructureConfiguration {
}
