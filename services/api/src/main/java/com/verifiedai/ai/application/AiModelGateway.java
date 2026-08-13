package com.verifiedai.ai.application;

public interface AiModelGateway {

    /*
     * Generic Sprint 5.1 execution contract.
     */
    default AiExecutionResult execute(
        AiExecutionCommand command
    ) {
        throw new UnsupportedOperationException(
            "Generic AI execution is not implemented"
        );
    }

    default AiRoutePlan routePlan(
        AiRouteContext context
    ) {
        return routePlan(
            context.capability()
        );
    }

    default AiRoutePlan routePlan(
        AiCapability capability
    ) {
        return routePlan(
            AiRouteContext.basic(
                capability,
                null
            )
        );
    }

    /*
     * Phase 4 compatibility methods.
     */
    default AiVisionParseResult executeVisionParse(
        AiVisionParseRequest request
    ) {
        return executeVisionParse(
            request,
            AiExecutionContext.compatibility(
                request.problemSessionId()
            )
        );
    }

    default AiVisionParseResult executeVisionParse(
        AiVisionParseRequest request,
        AiExecutionContext executionContext
    ) {
        return executeVisionParse(request);
    }

    default AiProblemNormalizeResult executeProblemNormalize(
        AiProblemNormalizeRequest request
    ) {
        return executeProblemNormalize(
            request,
            AiExecutionContext.compatibility(
                request.problemSessionId()
            )
        );
    }

    default AiProblemNormalizeResult executeProblemNormalize(
        AiProblemNormalizeRequest request,
        AiExecutionContext executionContext
    ) {
        return executeProblemNormalize(request);
    }

    default AiProblemClassifyResult executeProblemClassify(
        AiProblemClassifyRequest request
    ) {
        return executeProblemClassify(
            request,
            AiExecutionContext.compatibility(
                request.problemSessionId()
            )
        );
    }

    default AiProblemClassifyResult executeProblemClassify(
        AiProblemClassifyRequest request,
        AiExecutionContext executionContext
    ) {
        return executeProblemClassify(request);
    }
}
