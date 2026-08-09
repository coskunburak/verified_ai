package com.verifiedai.ai.infrastructure.provider;

import com.verifiedai.ai.application.AiProblemNormalizeRequest;
import com.verifiedai.ai.application.AiProblemNormalizeResult;
import com.verifiedai.ai.application.AiProviderException;
import com.verifiedai.ai.application.AiProviderFailureClass;
import com.verifiedai.ai.application.AiRoutePlan;
import org.springframework.stereotype.Component;

@Component
class UnavailableProblemNormalizeProviderAdapter implements ProblemNormalizeProviderAdapter {
    @Override
    public String providerId() {
        return "UNAVAILABLE";
    }

    @Override
    public AiProblemNormalizeResult execute(AiProblemNormalizeRequest request, AiRoutePlan routePlan) {
        throw new AiProviderException(
            AiProviderFailureClass.CONFIGURATION_DISABLED,
            false,
            "Problem parser provider is not configured"
        );
    }
}
