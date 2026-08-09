package com.verifiedai.ai.application;

public interface AiModelGateway {
    AiRoutePlan routePlan(AiCapability capability);

    AiVisionParseResult executeVisionParse(AiVisionParseRequest request);

    AiProblemNormalizeResult executeProblemNormalize(AiProblemNormalizeRequest request);
}
