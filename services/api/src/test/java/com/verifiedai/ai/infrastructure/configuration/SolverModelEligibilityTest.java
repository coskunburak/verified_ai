package com.verifiedai.ai.infrastructure.configuration;

import com.verifiedai.ai.VllmTestPolicy;
import com.verifiedai.ai.application.*;
import java.net.URI;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import static org.assertj.core.api.Assertions.*;

class SolverModelEligibilityTest {
    final VllmSolverProperties config=VllmTestPolicy.config(URI.create("http://127.0.0.1:8000/v1"));
    final AiRouteRegistryProperties.CapabilityRoute route=VllmTestPolicy.route(VllmSolverProperties.PROVIDER,VllmSolverProperties.MODEL,100);
    @Test void exactApprovedOfflineReleaseResolvesRealArtifactsAndRoute() {
        var loader=new DefaultResourceLoader();
        var validator=new PromptRegistryValidator(PromptRegistry.defaults(),OutputSchemaRegistry.defaults(),
            new ClasspathPromptArtifactResolver(loader),new ClasspathOutputSchemaArtifactResolver(loader));
        var planner=new ConfiguredAiRoutePlanner(
            new AiVisionRecognitionProperties(false,null,null,null,null,null,null,null,null,null,null,0,0,0,null,null),
            new AiProblemParserProperties(false,null,null,null,null,null,null,null,null,null,null,0,0,0,null,null),
            new AiProblemClassifierProperties(false,null,null,null,null,null,null,null,null,null,null,0,0,0,null,null),
            new AiRouteRegistryProperties(new AiRouteRegistryProperties.UsageLedger(true),Map.of(AiCapability.SOLVE,route)),
            AiCapabilityRegistry.defaults(),validator,"local",VllmTestPolicy.routing(),
            target->new AiProviderHealth.Snapshot(AiProviderHealth.State.CLOSED,java.time.Instant.now().minusSeconds(1),"synthetic-health"),
            AiEntitlementResolver.conservative(),Clock.systemUTC(),config);
        var command=VllmTestPolicy.command(com.verifiedai.solving.SolverTestData.source(),100,java.time.Instant.now().plusSeconds(30));
        assertThat(planner.routePlan(command.routeContext()).primary().model()).isEqualTo(VllmSolverProperties.MODEL);
        assertThat(planner.attemptCeiling(VllmTestPolicy.plan(),VllmTestPolicy.plan().primary())).isEqualTo(100);
    }
    @Test void disabledModelMissingLedgerMissingEligibilityAndInsufficientBudgetFailClosed() {
        assertThatThrownBy(()->SolverModelEligibility.validate(route,"local",true,VllmSolverProperties.disabled(),VllmTestPolicy.routing())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->SolverModelEligibility.validate(route,"local",false,config,VllmTestPolicy.routing())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->SolverModelEligibility.validate(route,"local",true,config,new AiRoutingProperties(null,null))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->SolverModelEligibility.validate(VllmTestPolicy.route(VllmSolverProperties.PROVIDER,VllmSolverProperties.MODEL,0),
            "local",true,config,VllmTestPolicy.routing())).isInstanceOf(IllegalStateException.class);
    }
    @Test void noProductionOrStagingActivationAndNoUnknownModelOrProvider() {
        for(String env:List.of("production","prod","staging","preview"))
            assertThatThrownBy(()->SolverModelEligibility.validate(route,env,true,config,VllmTestPolicy.routing())).isInstanceOf(IllegalStateException.class);
        for(var bad:List.of(VllmTestPolicy.route("UNKNOWN",VllmSolverProperties.MODEL,100),
            VllmTestPolicy.route(VllmSolverProperties.PROVIDER,"Qwen/Qwen3.5-4B",100)))
            assertThatThrownBy(()->SolverModelEligibility.validate(bad,"local",true,config,VllmTestPolicy.routing())).isInstanceOf(IllegalStateException.class);
    }
    @Test void wrongRevisionRuntimeAndUngovernedQuantizationAreRejected() {
        for(String revision:List.of("main","latest","c".repeat(40)))
            assertThatThrownBy(()->new VllmSolverProperties.Release(VllmSolverProperties.REPOSITORY,revision,
                VllmSolverProperties.REVISION,VllmSolverProperties.REVISION,"0.17.1","sha256:"+"a".repeat(64),"BF16_REFERENCE","b".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class);
        for(String quant:List.of("AWQ","INT4","GGUF","Q4_K_M"))
            assertThatThrownBy(()->new VllmSolverProperties.Release(VllmSolverProperties.REPOSITORY,VllmSolverProperties.REVISION,
                VllmSolverProperties.REVISION,VllmSolverProperties.REVISION,"0.17.1","sha256:"+"a".repeat(64),quant,"b".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new VllmSolverProperties.Release(VllmSolverProperties.REPOSITORY,VllmSolverProperties.REVISION,
            VllmSolverProperties.REVISION,VllmSolverProperties.REVISION,"latest","sha256:"+"a".repeat(64),"BF16_REFERENCE","b".repeat(64)))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void endpointAndCredentialPolicyCannotBeBypassed() {
        for(String url:List.of("http://example.com/v1","http://127.0.0.1:8000/v1?token=x",
            "https://user:password@example.com/v1","https://example.com/other"))
            assertThatThrownBy(()->VllmTestPolicy.config(URI.create(url))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new VllmSolverProperties(true,config.endpoint(),Set.of(),config.apiKey(),config.evaluationApproval(),
            config.release(),config.limits(),config.accounting())).isInstanceOf(IllegalArgumentException.class);
        assertThat(config.toString()).doesNotContain(config.apiKey());
    }
    @Test void offlinePromptDoesNotUnlockStagingOrProductionOrModifyFixturePrompt() {
        var loader=new DefaultResourceLoader();
        var validator=new PromptRegistryValidator(PromptRegistry.defaults(),OutputSchemaRegistry.defaults(),
            new ClasspathPromptArtifactResolver(loader),new ClasspathOutputSchemaArtifactResolver(loader));
        for(String env:List.of("staging","production"))
            assertThatThrownBy(()->validator.validate(VllmTestPolicy.plan(),env)).isInstanceOf(PromptRegistryValidationException.class);
        assertThat(PromptRegistry.defaults().require("solver","v001").contentSha256())
            .isEqualTo("2ba2e16b9cdc782e19e337469daab02d7da0cac5ac04ea046da4cdd26645a32e");
    }
}
