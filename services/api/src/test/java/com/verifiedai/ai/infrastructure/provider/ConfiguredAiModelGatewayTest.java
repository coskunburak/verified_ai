package com.verifiedai.ai.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.ai.application.AiCapability;
import com.verifiedai.ai.application.AiProviderException;
import com.verifiedai.ai.application.AiProviderFailureClass;
import com.verifiedai.ai.application.AiProvenance;
import com.verifiedai.ai.application.AiRoutePlan;
import com.verifiedai.ai.application.AiUsage;
import com.verifiedai.ai.application.AiVisionParseRequest;
import com.verifiedai.ai.application.AiVisionParseResult;
import com.verifiedai.ai.infrastructure.configuration.AiVisionRecognitionProperties;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ConfiguredAiModelGatewayTest {
    @Test
    void retryablePrimaryFailureUsesFallbackAndMarksProvenance() {
        ConfiguredAiModelGateway gateway = new ConfiguredAiModelGateway(
            properties("PRIMARY", "FALLBACK"),
            List.of(
                failingProvider("PRIMARY", AiProviderFailureClass.TIMEOUT, true),
                successfulProvider("FALLBACK", "fallback-model")
            ),
            "local"
        );

        AiVisionParseResult result = gateway.executeVisionParse(request());

        assertThat(result.provenance().provider()).isEqualTo("FALLBACK");
        assertThat(result.provenance().model()).isEqualTo("fallback-model");
        assertThat(result.provenance().fallbackUsed()).isTrue();
        assertThat(result.rawOutputJson()).contains("recognition-evidence-v1");
    }

    @Test
    void terminalPrimaryFailureDoesNotUseFallback() {
        ConfiguredAiModelGateway gateway = new ConfiguredAiModelGateway(
            properties("PRIMARY", "FALLBACK"),
            List.of(
                failingProvider("PRIMARY", AiProviderFailureClass.INVALID_AUTH, false),
                successfulProvider("FALLBACK", "fallback-model")
            ),
            "local"
        );

        assertThatThrownBy(() -> gateway.executeVisionParse(request()))
            .isInstanceOf(AiProviderException.class)
            .satisfies(exception -> {
                AiProviderException providerException = (AiProviderException) exception;
                assertThat(providerException.failureClass()).isEqualTo(AiProviderFailureClass.INVALID_AUTH);
                assertThat(providerException.retryable()).isFalse();
            });
    }

    @Test
    void productionConfigurationRejectsLocalFixtureProviderWhenEnabled() {
        ConfiguredAiModelGateway gateway = new ConfiguredAiModelGateway(
            properties("LOCAL_FIXTURE", ""),
            List.of(successfulProvider("LOCAL_FIXTURE", "local-fixture-vision-v1")),
            "production"
        );

        assertThatThrownBy(gateway::validateProductionConfiguration)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("LOCAL_FIXTURE");
    }

    private static AiVisionRecognitionProperties properties(String primaryProvider, String fallbackProvider) {
        return new AiVisionRecognitionProperties(
            true,
            primaryProvider,
            fallbackProvider,
            "vision-route-v1",
            "vision-recognition",
            "v001",
            "recognition-evidence-v1",
            Duration.ofSeconds(20),
            2,
            65_536,
            20_000,
            "test-pricing-v1"
        );
    }

    private static AiVisionParseRequest request() {
        return new AiVisionParseRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "image/jpeg",
            "image".getBytes(java.nio.charset.StandardCharsets.UTF_8),
            1200,
            900,
            "vision-recognition",
            "v001",
            "recognition-evidence-v1",
            Duration.ofSeconds(20),
            List.of("RESOLUTION:WARNING")
        );
    }

    private static VisionParseProviderAdapter successfulProvider(String providerId, String model) {
        return new VisionParseProviderAdapter() {
            @Override
            public String providerId() {
                return providerId;
            }

            @Override
            public AiVisionParseResult execute(AiVisionParseRequest request, AiRoutePlan routePlan) {
                return new AiVisionParseResult(
                    """
                    {"schemaVersion":"recognition-evidence-v1","blocks":[],"documentUncertainty":[],"reviewRequired":false}
                    """,
                    new AiProvenance(
                        providerId,
                        model,
                        routePlan.routePolicyVersion(),
                        request.promptId(),
                        request.promptVersion(),
                        request.schemaVersion(),
                        "request-id",
                        "response-id",
                        false
                    ),
                    new AiUsage(1, 2, 1, 1, 3, "USD", "test-pricing-v1"),
                    5
                );
            }
        };
    }

    private static VisionParseProviderAdapter failingProvider(
        String providerId,
        AiProviderFailureClass failureClass,
        boolean retryable
    ) {
        return new VisionParseProviderAdapter() {
            @Override
            public String providerId() {
                return providerId;
            }

            @Override
            public AiVisionParseResult execute(AiVisionParseRequest request, AiRoutePlan routePlan) {
                throw new AiProviderException(failureClass, retryable, "provider failed");
            }
        };
    }
}
