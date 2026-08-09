package com.verifiedai.ai.infrastructure.provider;

import com.verifiedai.ai.application.AiRoutePlan;
import com.verifiedai.ai.application.AiVisionParseRequest;
import com.verifiedai.ai.application.AiVisionParseResult;

interface VisionParseProviderAdapter {
    String providerId();

    AiVisionParseResult execute(AiVisionParseRequest request, AiRoutePlan routePlan);
}
