package com.verifiedai.ai.infrastructure.provider;

import com.verifiedai.ai.application.AiProblemClassifyRequest;
import com.verifiedai.ai.application.AiProblemClassifyResult;
import com.verifiedai.ai.application.AiRoutePlan;

interface ProblemClassifyProviderAdapter {
    String providerId();

    AiProblemClassifyResult execute(AiProblemClassifyRequest request, AiRoutePlan routePlan);
}
