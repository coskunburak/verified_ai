package com.verifiedai.ai.infrastructure.configuration;

import com.verifiedai.ai.application.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import static org.assertj.core.api.Assertions.*;

class SolverRouteConfigurationTest {
    AiRouteRegistryProperties.CapabilityRoute route(String provider,String model,String prompt,boolean enabled) {
        return new AiRouteRegistryProperties.CapabilityRoute(enabled,"solve-route-v1","solve-test-v1",
            new AiRouteRegistryProperties.RouteTarget(provider,model),List.of(),prompt,"v001","solution-candidate-v1",
            Duration.ofSeconds(30),1,65536,0,"fixture-free-v1",AiRoutePlan.CachePolicy.DISABLED,
            AiRoutePlan.StreamingPolicy.DISABLED,"NO_TRAINING_BY_DEFAULT","LOCAL_TEST","solve-local-fixture",null,AiRoutePlan.ReleaseStage.LOCAL_ONLY);
    }
    ConfiguredAiRoutePlanner planner(Map<AiCapability,AiRouteRegistryProperties.CapabilityRoute> entries,String env,boolean ledger) {
        var v=new AiVisionRecognitionProperties(false,null,null,null,null,null,null,null,null,null,null,0,0,0,null,null);
        var p=new AiProblemParserProperties(false,null,null,null,null,null,null,null,null,null,null,0,0,0,null,null);
        var c=new AiProblemClassifierProperties(false,null,null,null,null,null,null,null,null,null,null,0,0,0,null,null);
        var loader=new DefaultResourceLoader();
        var validator=new PromptRegistryValidator(PromptRegistry.defaults(),OutputSchemaRegistry.defaults(),
            new ClasspathPromptArtifactResolver(loader),new ClasspathOutputSchemaArtifactResolver(loader));
        return new ConfiguredAiRoutePlanner(v,p,c,new AiRouteRegistryProperties(new AiRouteRegistryProperties.UsageLedger(ledger),entries),
            AiCapabilityRegistry.defaults(),validator,env);
    }
    @Test void solveIsDisabledWithoutExplicitRoute() { assertThat(planner(Map.of(),"local",true).enabled(AiCapability.SOLVE)).isFalse(); }
    @Test void optedInLocalRouteResolvesRealPromptSchemaAndTrustedTaskEligibility() {
        var p=planner(Map.of(AiCapability.SOLVE,route("LOCAL_FIXTURE","local-fixture-solver-v1","solver",true)),"test",true);
        var context=new AiRouteContext(AiCapability.SOLVE,"EQUATION","SOLVE_EQUATION",null,null,false,"LOW",false,null,
            null,Duration.ofSeconds(30),0L,null,Map.of(),null,null,"NO_TRAINING_BY_DEFAULT",AiRouteContext.RouteRole.PRIMARY_SOLVER);
        assertThat(p.routePlan(context).schemaVersion()).isEqualTo("solution-candidate-v1");
        assertThat(p.attemptCeiling(p.routePlan(context),p.routePlan(context).primary())).isZero();
    }
    @Test void productionStagingAndUnapprovedProvidersFailClosed() {
        for(String env:List.of("production","staging","unknown"))
            assertThatThrownBy(()->planner(Map.of(AiCapability.SOLVE,route("LOCAL_FIXTURE","local-fixture-solver-v1","solver",true)),env,true))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->planner(Map.of(AiCapability.SOLVE,route("REMOTE","not-approved","solver",true)),"local",true))
            .isInstanceOf(IllegalStateException.class);
    }
    @Test void ledgerAndExactModelAreMandatoryEvenForFixture() {
        assertThatThrownBy(()->planner(Map.of(AiCapability.SOLVE,route("LOCAL_FIXTURE","local-fixture-solver-v1","solver",true)),"local",false))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->planner(Map.of(AiCapability.SOLVE,route("LOCAL_FIXTURE","wrong-model","solver",true)),"local",true))
            .isInstanceOf(IllegalStateException.class);
    }
    @Test void missingOrWrongCapabilityPromptFailsStartup() {
        for(String prompt:List.of("missing","vision-recognition"))
            assertThatThrownBy(()->planner(Map.of(AiCapability.SOLVE,route("LOCAL_FIXTURE","local-fixture-solver-v1",prompt,true)),"test",true))
                .isInstanceOf(PromptRegistryValidationException.class);
    }
    @Test void unrelatedFutureCapabilitiesStillCannotBeEnabled() {
        for(AiCapability c:List.of(AiCapability.EXPLAIN,AiCapability.ARBITRATE,AiCapability.TUTOR,AiCapability.PRACTICE_GENERATE))
            assertThatThrownBy(()->planner(Map.of(c,route("LOCAL_FIXTURE","local-fixture-solver-v1","solver",true)),"test",true))
                .isInstanceOf(IllegalStateException.class);
    }
}
