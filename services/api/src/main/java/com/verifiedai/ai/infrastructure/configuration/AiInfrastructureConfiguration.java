package com.verifiedai.ai.infrastructure.configuration;

import com.verifiedai.ai.application.AiCapabilityRegistry;
import com.verifiedai.ai.application.AiUsageRecorder;
import com.verifiedai.ai.application.OutputSchemaArtifactResolver;
import com.verifiedai.ai.application.OutputSchemaRegistry;
import com.verifiedai.ai.application.PromptArtifactResolver;
import com.verifiedai.ai.application.PromptRegistry;
import com.verifiedai.ai.application.PromptRegistryValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

@Configuration
@EnableConfigurationProperties({
    AiVisionRecognitionProperties.class,
    AiProblemParserProperties.class,
    AiProblemClassifierProperties.class,
    AiRouteRegistryProperties.class,
    AiRoutingProperties.class,
    AiBudgetProperties.class,
    VllmSolverProperties.class
})
class AiInfrastructureConfiguration {

    @Bean
    com.verifiedai.ai.application.AiProviderHealth aiProviderHealth(
        org.springframework.beans.factory.ObjectProvider<com.verifiedai.ai.infrastructure.provider.VllmSolveProviderAdapter> vllm) {
        return target -> {
            var adapter = vllm.getIfAvailable();
            return adapter == null ? com.verifiedai.ai.application.AiProviderHealth.unknown().snapshot(target)
                : adapter.health(target);
        };
    }

    @Bean
    com.verifiedai.ai.application.AiEntitlementResolver aiEntitlementResolver(
        com.verifiedai.billing.application.EntitlementApplicationService billing, java.time.Clock clock) {
        return userId -> {
            if (userId == null) return "FREE";
            return billing.currentAiTier(userId);
        };
    }

    @Bean
    AiCapabilityRegistry aiCapabilityRegistry() {
        return AiCapabilityRegistry.defaults();
    }

    @Bean
    PromptRegistry promptRegistry() {
        return PromptRegistry.defaults();
    }

    @Bean
    OutputSchemaRegistry outputSchemaRegistry() {
        return OutputSchemaRegistry.defaults();
    }

    @Bean
    PromptArtifactResolver promptArtifactResolver(
        ResourceLoader resourceLoader
    ) {
        return new ClasspathPromptArtifactResolver(
            resourceLoader
        );
    }

    @Bean
    OutputSchemaArtifactResolver outputSchemaArtifactResolver(
        ResourceLoader resourceLoader
    ) {
        return new ClasspathOutputSchemaArtifactResolver(
            resourceLoader
        );
    }

    @Bean
    PromptRegistryValidator promptRegistryValidator(
        PromptRegistry promptRegistry,
        OutputSchemaRegistry outputSchemaRegistry,
        PromptArtifactResolver promptArtifactResolver,
        OutputSchemaArtifactResolver outputSchemaArtifactResolver,
        ResourceLoader resourceLoader
    ) {
        return new PromptRegistryValidator(
            promptRegistry,
            outputSchemaRegistry,
            promptArtifactResolver,
            outputSchemaArtifactResolver,
            new ClasspathSolverReleaseEvidenceValidator(resourceLoader)
        );
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
