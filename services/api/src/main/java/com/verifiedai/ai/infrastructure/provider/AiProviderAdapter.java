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

    /** Must enforce this upper bound at the provider, including failed/timeout charges. */
    default boolean enforcesCostCeiling() { return false; }

    default boolean supports(
        AiCapability capability
    ) {
        return supportedCapabilities()
            .contains(capability);
    }
}
