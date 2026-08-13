package com.verifiedai.ai.application;

public interface AiGatewayMetrics {

    void request(
        AiRoutePlan routePlan
    );

    void result(
        AiRoutePlan routePlan,
        AiRouteTarget target,
        AiExecutionResult result
    );

    void retry(
        AiRoutePlan routePlan,
        AiRouteTarget failedTarget
    );

    void fallback(
        AiRoutePlan routePlan,
        AiRouteTarget target
    );

    void blocked(
        AiCapability capability,
        AiExecutionStatus status
    );

    void ledgerWrite(
        AiCapability capability,
        String outcome
    );

    static AiGatewayMetrics noOp() {
        return new AiGatewayMetrics() {

            @Override
            public void request(
                AiRoutePlan routePlan
            ) {
            }

            @Override
            public void result(
                AiRoutePlan routePlan,
                AiRouteTarget target,
                AiExecutionResult result
            ) {
            }

            @Override
            public void retry(
                AiRoutePlan routePlan,
                AiRouteTarget failedTarget
            ) {
            }

            @Override
            public void fallback(
                AiRoutePlan routePlan,
                AiRouteTarget target
            ) {
            }

            @Override
            public void blocked(
                AiCapability capability,
                AiExecutionStatus status
            ) {
            }

            @Override
            public void ledgerWrite(
                AiCapability capability,
                String outcome
            ) {
            }
        };
    }
}
