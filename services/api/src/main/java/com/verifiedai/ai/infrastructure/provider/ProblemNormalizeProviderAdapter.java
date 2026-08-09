package com.verifiedai.ai.infrastructure.provider;

import com.verifiedai.ai.application.AiProblemNormalizeRequest;
import com.verifiedai.ai.application.AiProblemNormalizeResult;
import com.verifiedai.ai.application.AiRoutePlan;

interface ProblemNormalizeProviderAdapter {
    String providerId();

    AiProblemNormalizeResult execute(AiProblemNormalizeRequest request, AiRoutePlan routePlan);
}
