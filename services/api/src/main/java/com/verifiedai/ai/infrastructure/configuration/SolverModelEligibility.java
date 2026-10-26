package com.verifiedai.ai.infrastructure.configuration;

import com.verifiedai.ai.application.AiCapability;
import com.verifiedai.ai.application.AiRoutePlan;

/** A research selection permits implementation; only explicit local evaluation approval permits dispatch. */
final class SolverModelEligibility {
    private SolverModelEligibility() { }

    static void validate(AiRouteRegistryProperties.CapabilityRoute route, String environment,
        boolean ledger, VllmSolverProperties model, AiRoutingProperties routing) {
        boolean local = "local".equalsIgnoreCase(environment) || "test".equalsIgnoreCase(environment);
        if (!local || !ledger || route.primary() == null || !route.fallbacks().isEmpty()
            || route.maxAttempts() != 1 || route.releaseStage() != AiRoutePlan.ReleaseStage.LOCAL_ONLY) {
            throw blocked();
        }
        if ("LOCAL_FIXTURE".equals(route.primary().provider())) {
            if (!"local-fixture-solver-v1".equals(route.primary().model()) || route.maxCostMicros() != 0) throw blocked();
            return;
        }
        if (!model.enabled() || !VllmSolverProperties.PROVIDER.equals(route.primary().provider())
            || !VllmSolverProperties.MODEL.equals(route.primary().model())
            || !VllmSolverProperties.ROUTE.equals(route.routePolicyVersion())
            || !VllmSolverProperties.GATE.equals(route.qualityGateVersion())
            || !"solver".equals(route.promptId()) || !"v002".equals(route.promptVersion())
            || !"solution-candidate-v1".equals(route.schemaVersion())
            || !model.accounting().pricingVersion().equals(route.pricingVersion())
            || route.maxCostMicros() < model.accounting().attemptAllocationMicros()
            || route.timeout() == null || route.timeout().compareTo(model.limits().responseTimeout()) > 0
            || route.maxResponseBytes() != 65536 || !"ZERO_RETENTION".equals(route.providerRetentionPolicy())
            || route.cachePolicy() != AiRoutePlan.CachePolicy.DISABLED
            || route.streamingPolicy() != AiRoutePlan.StreamingPolicy.DISABLED) throw blocked();
        var eligible = routing.eligibility().stream().filter(e -> e.capability() == AiCapability.SOLVE
            && VllmSolverProperties.PROVIDER.equals(e.target().provider())
            && VllmSolverProperties.MODEL.equals(e.target().model())).toList();
        if (eligible.size() != 1 || !eligible.getFirst().approved()
            || eligible.getFirst().attemptCeilingMicros() != model.accounting().attemptAllocationMicros()) throw blocked();
    }

    private static IllegalStateException blocked() {
        return new IllegalStateException("SOLVE activation blocked: exact approved offline model policy required; production disabled");
    }
}
