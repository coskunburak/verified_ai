package com.verifiedai.ai.infrastructure.provider;

import com.verifiedai.ai.application.AiCapability;
import com.verifiedai.ai.application.AiCapabilityResult;
import java.util.Set;

public interface AiProviderAdapter {

    String providerId();

    Set<AiCapability> supportedCapabilities();

    AiCapabilityResult execute(
        AiProviderRequest request
    );

    default boolean supports(
        AiCapability capability
    ) {
        return supportedCapabilities()
            .contains(capability);
    }
}
