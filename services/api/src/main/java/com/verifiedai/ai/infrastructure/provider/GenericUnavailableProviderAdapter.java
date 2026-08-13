package com.verifiedai.ai.infrastructure.provider;

import com.verifiedai.ai.application.AiCapability;
import com.verifiedai.ai.application.AiCapabilityResult;
import com.verifiedai.ai.application.AiProviderException;
import com.verifiedai.ai.application.AiProviderFailureClass;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
final class GenericUnavailableProviderAdapter
    implements AiProviderAdapter {

    @Override
    public String providerId() {
        return "UNAVAILABLE";
    }

    @Override
    public Set<AiCapability> supportedCapabilities() {
        return EnumSet.allOf(
            AiCapability.class
        );
    }

    @Override
    public AiCapabilityResult execute(
        AiProviderRequest request
    ) {
        throw new AiProviderException(
            AiProviderFailureClass.PROVIDER_UNAVAILABLE,
            true,
            "Configured AI provider is unavailable"
        );
    }
}
