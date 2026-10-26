package com.verifiedai.ai;

import com.verifiedai.ai.application.*;
import com.verifiedai.ai.infrastructure.configuration.*;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Synthetic configuration only; digest strings are deliberately test doubles, not release evidence. */
public final class VllmTestPolicy {
    private VllmTestPolicy() { }
    public static final String CREDENTIAL = "not_a_secret_synthetic_test_credential";
    public static VllmSolverProperties config(URI endpoint) {
        return new VllmSolverProperties(true, endpoint, Set.of(endpoint.getScheme()+"://"+endpoint.getRawAuthority()),
            CREDENTIAL, "test-only-approval", release(), new VllmSolverProperties.Limits(8192,6144,2048,65536,131072,
                Duration.ofSeconds(1),Duration.ofSeconds(30),2,false),
            new VllmSolverProperties.Accounting("test-allocation-v1","PREPAID_CAPACITY_FIXED_ALLOCATION",100,40,60,0));
    }
    public static VllmSolverProperties.Release release() {
        return new VllmSolverProperties.Release(VllmSolverProperties.REPOSITORY,VllmSolverProperties.REVISION,
            VllmSolverProperties.REVISION,VllmSolverProperties.REVISION,"0.17.1","sha256:"+"a".repeat(64),
            "BF16_REFERENCE","b".repeat(64));
    }
    public static AiRouteRegistryProperties.CapabilityRoute route(String provider,String model,long budget) {
        return new AiRouteRegistryProperties.CapabilityRoute(true,VllmSolverProperties.ROUTE,"qwen-evaluation-v1",
            new AiRouteRegistryProperties.RouteTarget(provider,model),List.of(),"solver","v002","solution-candidate-v1",
            Duration.ofSeconds(30),1,65536,budget,"test-allocation-v1",AiRoutePlan.CachePolicy.DISABLED,
            AiRoutePlan.StreamingPolicy.DISABLED,"ZERO_RETENTION","LOCAL_TEST","qwen-evaluation",
            VllmSolverProperties.GATE,AiRoutePlan.ReleaseStage.LOCAL_ONLY);
    }
    public static AiRoutePlan plan() {
        var c=route(VllmSolverProperties.PROVIDER,VllmSolverProperties.MODEL,100);
        return new AiRoutePlan(AiCapability.SOLVE,c.routePolicyVersion(),c.routeId(),
            new AiRouteTarget(c.primary().provider(),c.primary().model()),List.of(),c.promptId(),c.promptVersion(),c.schemaVersion(),
            c.timeout(),c.maxAttempts(),c.maxResponseBytes(),c.maxCostMicros(),c.pricingVersion(),c.cachePolicy(),c.streamingPolicy(),
            c.providerRetentionPolicy(),c.privacyRegion(),c.circuitBreakerKey(),c.qualityGateVersion(),c.releaseStage());
    }
    public static AiRoutingProperties routing() {
        return new AiRoutingProperties(Duration.ofSeconds(30),List.of(new AiRouteEligibility(AiCapability.SOLVE,plan().primary(),
            VllmSolverProperties.GATE,100,Duration.ofSeconds(30),Set.of("EVALUATE","SIMPLIFY","SOLVE_EQUATION","SOLVE_INEQUALITY"),
            Set.of("UNKNOWN","EASY"),Set.of("FREE"),"LOCAL_TEST","ZERO_RETENTION",true)));
    }
    public static AiExecutionCommand command(com.verifiedai.problem.application.solving.SolveSource s,long budget,Instant deadline) {
        var c=new AiRouteContext(AiCapability.SOLVE,s.problemType(),s.taskType(),null,null,false,"LOW",false,null,
            Locale.forLanguageTag("tr"),Duration.ofSeconds(30),budget,null,Map.of(),null,"LOCAL_TEST","ZERO_RETENTION",
            AiRouteContext.RouteRole.PRIMARY_SOLVER);
        return new AiExecutionCommand(AiCapability.SOLVE,new AiSolveRequest(s.canonicalProblemId(),s.problemParseId(),
            s.problemParseRevision(),s.canonicalJson()),new AiExecutionContext(UUID.randomUUID(),s.userId(),s.problemSessionId(),
            "test-correlation",null,"test-idempotency",deadline,Map.of()),c,"application/json","canonical-problem-v1",
            "solver","v002","solution-candidate-v1",budget);
    }
}
