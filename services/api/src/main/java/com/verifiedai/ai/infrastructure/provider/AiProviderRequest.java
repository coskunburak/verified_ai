package com.verifiedai.ai.infrastructure.provider;

import com.verifiedai.ai.application.AiExecutionCommand;
import com.verifiedai.ai.application.AiRoutePlan;
import com.verifiedai.ai.application.AiRouteTarget;
import java.util.Objects;

public record AiProviderRequest(
    AiExecutionCommand command,
    AiRoutePlan routePlan,
    AiRouteTarget target
) {

    public AiProviderRequest {
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
