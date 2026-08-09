package com.verifiedai.ai.infrastructure.provider;

import com.verifiedai.ai.application.AiProviderException;
import com.verifiedai.ai.application.AiProviderFailureClass;
import com.verifiedai.ai.application.AiRoutePlan;
import com.verifiedai.ai.application.AiVisionParseRequest;
import com.verifiedai.ai.application.AiVisionParseResult;
import org.springframework.stereotype.Component;

@Component
class UnavailableVisionParseProviderAdapter implements VisionParseProviderAdapter {
    @Override
    public String providerId() {
        return "UNAVAILABLE";
    }

    @Override
    public AiVisionParseResult execute(AiVisionParseRequest request, AiRoutePlan routePlan) {
        throw new AiProviderException(
            AiProviderFailureClass.CONFIGURATION_DISABLED,
            false,
            "Vision recognition provider is not configured"
        );
    }
}
