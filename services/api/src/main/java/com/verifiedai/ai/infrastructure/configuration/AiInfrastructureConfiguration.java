package com.verifiedai.ai.infrastructure.configuration;

import com.verifiedai.ai.application.AiCapabilityRegistry;
import com.verifiedai.ai.application.AiUsageRecorder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
    AiVisionRecognitionProperties.class,
    AiProblemParserProperties.class,
    AiProblemClassifierProperties.class,
    AiRouteRegistryProperties.class
})
class AiInfrastructureConfiguration {

    @Bean
    AiCapabilityRegistry aiCapabilityRegistry() {
        return AiCapabilityRegistry.defaults();
    }

    @Bean
    @ConditionalOnProperty(
        name = "app.ai.usage-ledger.enabled",
        havingValue = "false"
    )
    AiUsageRecorder noOpAiUsageRecorder() {
        return AiUsageRecorder.noOp();
    }
}
