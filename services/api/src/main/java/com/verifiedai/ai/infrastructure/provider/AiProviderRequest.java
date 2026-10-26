package com.verifiedai.ai.infrastructure.provider;

import com.verifiedai.ai.application.AiExecutionCommand;
import com.verifiedai.ai.application.AiRoutePlan;
import com.verifiedai.ai.application.AiRouteTarget;
import java.util.Objects;

public record AiProviderRequest(
    AiExecutionCommand command,
    AiRoutePlan routePlan,
    AiRouteTarget target,
    java.time.Duration attemptTimeout,
    long costCeilingMicros
) {

    public AiProviderRequest(AiExecutionCommand command, AiRoutePlan routePlan, AiRouteTarget target) {
        this(command, routePlan, target, routePlan.timeout(), routePlan.maxCostMicros());
    }

    public AiProviderRequest {
        if (attemptTimeout == null || attemptTimeout.isZero() || attemptTimeout.isNegative() || costCeilingMicros < 0) {
            throw new IllegalArgumentException("Invalid provider execution bounds");
        }
        Objects.requireNonNull(
            command,
            "command is required"
        );
        Objects.requireNonNull(
            routePlan,
            "routePlan is required"
        );
        Objects.requireNonNull(
            target,
            "target is required"
        );
    }
}
