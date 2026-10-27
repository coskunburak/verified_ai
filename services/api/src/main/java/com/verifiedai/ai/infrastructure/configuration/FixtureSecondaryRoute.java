package com.verifiedai.ai.infrastructure.configuration;

import com.verifiedai.ai.application.*;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Only synthetic local approval exists. Adding a real approved route belongs in AI infrastructure. */
@Component
public final class FixtureSecondaryRoute implements AiSecondaryRoute {
    public static final String MODEL = "local-fixture-secondary-solver-v1";
    private final boolean enabled;
    private final AiRoutePlan plan;
    public FixtureSecondaryRoute(@Value("${app.ai.secondary-fixture-enabled:false}") boolean enabled,
        @Value("${app.environment:local}") String environment,
        @Value("${app.ai.usage-ledger.enabled:true}") boolean ledger, PromptRegistryValidator validator) {
        this.enabled=enabled;
        if(enabled && (!Set.of("local","test").contains(environment) || !ledger))
            throw new IllegalStateException("Secondary fixture requires local/test environment and durable ledger");
        plan=new AiRoutePlan(AiCapability.SOLVE,"secondary-route-v1","secondary-fixture-v1",
            new AiRouteTarget("LOCAL_FIXTURE",MODEL),List.of(),"secondary-solver","v001",
            "solution-candidate-v1",Duration.ofSeconds(30),1,65536,0,"secondary-fixture-free-v1",
            AiRoutePlan.CachePolicy.DISABLED,AiRoutePlan.StreamingPolicy.DISABLED,"NO_TRAINING_BY_DEFAULT",
            "LOCAL_TEST","secondary-fixture-v1","synthetic-secondary-v1",AiRoutePlan.ReleaseStage.LOCAL_ONLY);
        if(enabled) validator.validateEnabledRoutes(List.of(AiRoutePolicy.enabled(plan)),environment);
    }
    @Override public AiRoutePlan plan(AiRouteContext context) {
        if(!enabled || context.capability()!=AiCapability.SOLVE
            || context.routeRole()!=AiRouteContext.RouteRole.SECONDARY_SOLVER)
            throw new AiProviderException(AiProviderFailureClass.CONFIGURATION_DISABLED,false,"No approved secondary model");
        var eligible=new AiRouteEligibility(AiCapability.SOLVE,plan.primary(),"synthetic-secondary-v1",0,
            Duration.ofMillis(1),Set.of("EVALUATE","SIMPLIFY","SOLVE_EQUATION","SOLVE_INEQUALITY"),
            Set.of("UNKNOWN"),Set.of("FREE","PRO","PRO_PLUS"),"LOCAL_TEST","NO_TRAINING_BY_DEFAULT",true);
        new AiRouteSelector().select(context,"FREE",List.of(eligible),AiProviderHealth.unknown(),
            java.time.Instant.EPOCH,Duration.ofSeconds(1));
        return plan;
    }
    @Override public long attemptCeiling(AiRoutePlan selected,AiRouteTarget target) {
        if(!enabled || !plan.equals(selected) || !plan.primary().equals(target))
            throw new AiProviderException(AiProviderFailureClass.POLICY_BLOCKED,false,"Secondary route changed");
        return 0;
    }
}
