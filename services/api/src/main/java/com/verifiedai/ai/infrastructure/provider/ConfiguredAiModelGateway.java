package com.verifiedai.ai.infrastructure.provider;

import com.verifiedai.ai.application.AiCapability;
import com.verifiedai.ai.application.AiModelGateway;
import com.verifiedai.ai.application.AiProviderException;
import com.verifiedai.ai.application.AiProviderFailureClass;
import com.verifiedai.ai.application.AiProvenance;
import com.verifiedai.ai.application.AiRoutePlan;
import com.verifiedai.ai.application.AiVisionParseRequest;
import com.verifiedai.ai.application.AiVisionParseResult;
import com.verifiedai.ai.infrastructure.configuration.AiVisionRecognitionProperties;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
class ConfiguredAiModelGateway implements AiModelGateway {
    private final AiVisionRecognitionProperties properties;
    private final Map<String, VisionParseProviderAdapter> providers;
    private final String environment;

    ConfiguredAiModelGateway(
        AiVisionRecognitionProperties properties,
        List<VisionParseProviderAdapter> providers,
        @Value("${app.environment:local}") String environment
    ) {
        this.properties = properties;
        this.providers = providers.stream().collect(Collectors.toMap(
            provider -> provider.providerId().toUpperCase(Locale.ROOT),
            Function.identity()
        ));
        this.environment = environment;
    }

    @PostConstruct
    void validateProductionConfiguration() {
        if (!properties.enabled()) {
            return;
        }
        if ("production".equalsIgnoreCase(environment) && "LOCAL_FIXTURE".equalsIgnoreCase(properties.primaryProvider())) {
            throw new IllegalStateException("LOCAL_FIXTURE vision recognition provider cannot be enabled in production");
        }
    }

    @Override
    public AiRoutePlan routePlan(AiCapability capability) {
        if (capability != AiCapability.VISION_PARSE) {
            throw new AiProviderException(AiProviderFailureClass.UNSUPPORTED_PAYLOAD, false, "Unsupported AI capability");
        }
        return new AiRoutePlan(
            capability,
            properties.routePolicyVersion(),
            normalize(properties.primaryProvider()),
            normalize(properties.fallbackProvider()),
            properties.promptId(),
            properties.promptVersion(),
            properties.schemaVersion(),
            properties.timeout(),
            properties.maxAttempts(),
            properties.maxResponseBytes(),
            properties.maxCostMicros(),
            properties.pricingVersion()
        );
    }

    @Override
    public AiVisionParseResult executeVisionParse(AiVisionParseRequest request) {
        if (!properties.enabled()) {
            throw new AiProviderException(
                AiProviderFailureClass.CONFIGURATION_DISABLED,
                false,
                "Vision recognition capability is disabled"
            );
        }
        AiRoutePlan routePlan = routePlan(AiCapability.VISION_PARSE);
        VisionParseProviderAdapter primary = provider(routePlan.primaryProvider());
        try {
            return primary.execute(request, routePlan);
        } catch (AiProviderException exception) {
            if (!exception.retryable() || routePlan.fallbackProvider() == null || routePlan.fallbackProvider().isBlank()) {
                throw exception;
            }
            VisionParseProviderAdapter fallback = provider(routePlan.fallbackProvider());
            AiVisionParseResult fallbackResult = fallback.execute(request, routePlan);
            AiProvenance provenance = fallbackResult.provenance();
            return new AiVisionParseResult(
                fallbackResult.rawOutputJson(),
                new AiProvenance(
                    provenance.provider(),
                    provenance.model(),
                    provenance.routePolicyVersion(),
                    provenance.promptId(),
                    provenance.promptVersion(),
                    provenance.schemaVersion(),
                    provenance.providerRequestId(),
                    provenance.providerResponseId(),
                    true
                ),
                fallbackResult.usage(),
                fallbackResult.providerLatencyMs()
            );
        }
    }

    private VisionParseProviderAdapter provider(String providerId) {
        VisionParseProviderAdapter provider = providers.get(normalize(providerId));
        if (provider == null) {
            throw new AiProviderException(
                AiProviderFailureClass.CONFIGURATION_DISABLED,
                false,
                "Vision recognition provider is not registered"
            );
        }
        return provider;
    }

    private static String normalize(String providerId) {
        return providerId == null || providerId.isBlank() ? null : providerId.toUpperCase(Locale.ROOT);
    }
}
