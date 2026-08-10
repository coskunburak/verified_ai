package com.verifiedai.ai.infrastructure.provider;

import com.verifiedai.ai.application.AiProblemClassifyRequest;
import com.verifiedai.ai.application.AiProblemClassifyResult;
import com.verifiedai.ai.application.AiProviderException;
import com.verifiedai.ai.application.AiProviderFailureClass;
import com.verifiedai.ai.application.AiRoutePlan;
import org.springframework.stereotype.Component;

@Component
class UnavailableProblemClassifyProviderAdapter implements ProblemClassifyProviderAdapter {
    @Override
    public String providerId() {
        return "UNAVAILABLE";
    }

    @Override
    public AiProblemClassifyResult execute(AiProblemClassifyRequest request, AiRoutePlan routePlan) {
        throw new AiProviderException(
            AiProviderFailureClass.CONFIGURATION_DISABLED,
            false,
            "Problem classifier provider is not configured"
        );
    }
}
