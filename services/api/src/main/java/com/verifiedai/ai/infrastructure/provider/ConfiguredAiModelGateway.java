package com.verifiedai.ai.infrastructure.provider;

import com.verifiedai.ai.application.AiCapability;
import com.verifiedai.ai.application.AiModelGateway;
import com.verifiedai.ai.application.AiProblemClassifyRequest;
import com.verifiedai.ai.application.AiProblemClassifyResult;
import com.verifiedai.ai.application.AiProblemNormalizeRequest;
import com.verifiedai.ai.application.AiProblemNormalizeResult;
import com.verifiedai.ai.application.AiProviderException;
import com.verifiedai.ai.application.AiProviderFailureClass;
import com.verifiedai.ai.application.AiProvenance;
import com.verifiedai.ai.application.AiRoutePlan;
import com.verifiedai.ai.application.AiVisionParseRequest;
import com.verifiedai.ai.application.AiVisionParseResult;
import com.verifiedai.ai.infrastructure.configuration.AiProblemClassifierProperties;
import com.verifiedai.ai.infrastructure.configuration.AiProblemParserProperties;
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
    private final AiVisionRecognitionProperties visionProperties;
    private final AiProblemParserProperties parserProperties;
    private final AiProblemClassifierProperties classifierProperties;
    private final Map<String, VisionParseProviderAdapter> visionProviders;
    private final Map<String, ProblemNormalizeProviderAdapter> parserProviders;
    private final Map<String, ProblemClassifyProviderAdapter> classifierProviders;
    private final String environment;

    ConfiguredAiModelGateway(
        AiVisionRecognitionProperties visionProperties,
        AiProblemParserProperties parserProperties,
        AiProblemClassifierProperties classifierProperties,
        List<VisionParseProviderAdapter> visionProviders,
        List<ProblemNormalizeProviderAdapter> parserProviders,
        List<ProblemClassifyProviderAdapter> classifierProviders,
        @Value("${app.environment:local}") String environment
    ) {
        this.visionProperties = visionProperties;
        this.parserProperties = parserProperties;
        this.classifierProperties = classifierProperties;
        this.visionProviders = visionProviders.stream().collect(Collectors.toMap(
            provider -> provider.providerId().toUpperCase(Locale.ROOT),
            Function.identity()
        ));
        this.parserProviders = parserProviders.stream().collect(Collectors.toMap(
            provider -> provider.providerId().toUpperCase(Locale.ROOT),
            Function.identity()
        ));
        this.classifierProviders = classifierProviders.stream().collect(Collectors.toMap(
            provider -> provider.providerId().toUpperCase(Locale.ROOT),
            Function.identity()
        ));
        this.environment = environment;
    }

    @PostConstruct
    void validateProductionConfiguration() {
        if (!"production".equalsIgnoreCase(environment)) {
            return;
        }
        if (visionProperties.enabled() && "LOCAL_FIXTURE".equalsIgnoreCase(visionProperties.primaryProvider())) {
            throw new IllegalStateException("LOCAL_FIXTURE vision recognition provider cannot be enabled in production");
        }
        if (parserProperties.enabled() && "LOCAL_FIXTURE".equalsIgnoreCase(parserProperties.primaryProvider())) {
            throw new IllegalStateException("LOCAL_FIXTURE problem parser provider cannot be enabled in production");
        }
        if (classifierProperties.enabled() && "LOCAL_FIXTURE".equalsIgnoreCase(classifierProperties.primaryProvider())) {
            throw new IllegalStateException("LOCAL_FIXTURE problem classifier provider cannot be enabled in production");
        }
    }

    @Override
    public AiRoutePlan routePlan(AiCapability capability) {
        return switch (capability) {
            case VISION_PARSE -> new AiRoutePlan(
                capability,
                visionProperties.routePolicyVersion(),
                normalize(visionProperties.primaryProvider()),
                normalize(visionProperties.fallbackProvider()),
                visionProperties.promptId(),
                visionProperties.promptVersion(),
                visionProperties.schemaVersion(),
                visionProperties.timeout(),
                visionProperties.maxAttempts(),
                visionProperties.maxResponseBytes(),
                visionProperties.maxCostMicros(),
                visionProperties.pricingVersion()
            );
            case PROBLEM_NORMALIZE -> new AiRoutePlan(
                capability,
                parserProperties.routePolicyVersion(),
                normalize(parserProperties.primaryProvider()),
                normalize(parserProperties.fallbackProvider()),
                parserProperties.promptId(),
                parserProperties.promptVersion(),
                parserProperties.schemaVersion(),
                parserProperties.timeout(),
                parserProperties.maxAttempts(),
                parserProperties.maxResponseBytes(),
                parserProperties.maxCostMicros(),
                parserProperties.pricingVersion()
            );
            case PROBLEM_CLASSIFY -> new AiRoutePlan(
                capability,
                classifierProperties.routePolicyVersion(),
                normalize(classifierProperties.primaryProvider()),
                normalize(classifierProperties.fallbackProvider()),
                classifierProperties.promptId(),
                classifierProperties.promptVersion(),
                classifierProperties.schemaVersion(),
                classifierProperties.timeout(),
                classifierProperties.maxAttempts(),
                classifierProperties.maxResponseBytes(),
                classifierProperties.maxCostMicros(),
                classifierProperties.pricingVersion()
            );
        };
    }

    @Override
    public AiVisionParseResult executeVisionParse(AiVisionParseRequest request) {
        if (!visionProperties.enabled()) {
            throw new AiProviderException(
                AiProviderFailureClass.CONFIGURATION_DISABLED,
                false,
                "Vision recognition capability is disabled"
            );
        }
        AiRoutePlan routePlan = routePlan(AiCapability.VISION_PARSE);
        VisionParseProviderAdapter primary = visionProvider(routePlan.primaryProvider());
        try {
            return primary.execute(request, routePlan);
        } catch (AiProviderException exception) {
            if (!exception.retryable() || routePlan.fallbackProvider() == null || routePlan.fallbackProvider().isBlank()) {
                throw exception;
            }
            VisionParseProviderAdapter fallback = visionProvider(routePlan.fallbackProvider());
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

    @Override
    public AiProblemNormalizeResult executeProblemNormalize(AiProblemNormalizeRequest request) {
        if (!parserProperties.enabled()) {
            throw new AiProviderException(
                AiProviderFailureClass.CONFIGURATION_DISABLED,
                false,
                "Problem parser capability is disabled"
            );
        }
        AiRoutePlan routePlan = routePlan(AiCapability.PROBLEM_NORMALIZE);
        ProblemNormalizeProviderAdapter primary = parserProvider(routePlan.primaryProvider());
        try {
            return primary.execute(request, routePlan);
        } catch (AiProviderException exception) {
            if (!exception.retryable() || routePlan.fallbackProvider() == null || routePlan.fallbackProvider().isBlank()) {
                throw exception;
            }
            ProblemNormalizeProviderAdapter fallback = parserProvider(routePlan.fallbackProvider());
            AiProblemNormalizeResult fallbackResult = fallback.execute(request, routePlan);
            AiProvenance provenance = fallbackResult.provenance();
            return new AiProblemNormalizeResult(
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

    private VisionParseProviderAdapter visionProvider(String providerId) {
        VisionParseProviderAdapter provider = visionProviders.get(normalize(providerId));
        if (provider == null) {
            throw new AiProviderException(
                AiProviderFailureClass.CONFIGURATION_DISABLED,
                false,
                "Vision recognition provider is not registered"
            );
        }
        return provider;
    }

    private ProblemNormalizeProviderAdapter parserProvider(String providerId) {
        ProblemNormalizeProviderAdapter provider = parserProviders.get(normalize(providerId));
        if (provider == null) {
            throw new AiProviderException(
                AiProviderFailureClass.CONFIGURATION_DISABLED,
                false,
                "Problem parser provider is not registered"
            );
        }
        return provider;
    }

    private ProblemClassifyProviderAdapter classifierProvider(String providerId) {
        ProblemClassifyProviderAdapter provider = classifierProviders.get(normalize(providerId));
        if (provider == null) {
            throw new AiProviderException(
                AiProviderFailureClass.CONFIGURATION_DISABLED,
                false,
                "Problem classifier provider is not registered"
            );
        }
        return provider;
    }

    @Override
    public AiProblemClassifyResult executeProblemClassify(AiProblemClassifyRequest request) {
        if (!classifierProperties.enabled()) {
            throw new AiProviderException(
                AiProviderFailureClass.CONFIGURATION_DISABLED,
                false,
                "Problem classifier capability is disabled"
            );
        }
        AiRoutePlan routePlan = routePlan(AiCapability.PROBLEM_CLASSIFY);
        ProblemClassifyProviderAdapter primary = classifierProvider(routePlan.primaryProvider());
        try {
            return primary.execute(request, routePlan);
        } catch (AiProviderException exception) {
            if (!exception.retryable() || routePlan.fallbackProvider() == null || routePlan.fallbackProvider().isBlank()) {
                throw exception;
            }
            ProblemClassifyProviderAdapter fallback = classifierProvider(routePlan.fallbackProvider());
            AiProblemClassifyResult fallbackResult = fallback.execute(request, routePlan);
            AiProvenance provenance = fallbackResult.provenance();
            return new AiProblemClassifyResult(
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

    private static String normalize(String providerId) {
        return providerId == null || providerId.isBlank() ? null : providerId.toUpperCase(Locale.ROOT);
    }
}
