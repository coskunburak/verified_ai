package com.verifiedai.ai.infrastructure.provider;

import com.verifiedai.ai.application.*;
import com.verifiedai.integration.solving.SolverFixtureConfiguration;
import com.verifiedai.solving.SolverTestData;
import com.verifiedai.solving.application.SolutionCandidateValidator;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SolverFixtureAdapterTest {
    @Test void productionArtifactHasNoSolverImplementationByDefault() {
        var adapter=new GenericLocalFixtureProviderAdapter(null,null,null);
        assertThat(adapter.supportedCapabilities()).doesNotContain(AiCapability.SOLVE);
    }
    @Test void explicitTestFixtureUsesProviderNeutralInputAndProvenance() {
        var source=SolverTestData.source();
        var plan=new AiRoutePlan(AiCapability.SOLVE,"solve-route-v1","LOCAL_FIXTURE",null,"solver","v001",
            "solution-candidate-v1",Duration.ofSeconds(1),1,65536,0,"free-fixture-v1");
        var command=new AiExecutionCommand(AiCapability.SOLVE,new AiSolveRequest(source.canonicalProblemId(),source.problemParseId(),1,source.canonicalJson()),
            AiExecutionContext.forJobAttempt(java.util.UUID.randomUUID(),1,source.userId(),source.problemSessionId(),"test"),
            AiRouteContext.basic(AiCapability.SOLVE,Duration.ofSeconds(1)),"application/json","canonical-problem-v1","solver","v001","solution-candidate-v1",0L);
        var adapter=new GenericLocalFixtureProviderAdapter(null,null,null);
        adapter.solverFixture(new SolverFixtureConfiguration.Fixture());
        var result=adapter.execute(new AiProviderRequest(command,plan,plan.primary()));
        assertThat(result.usage().estimatedCostMicros()).isZero();
        assertThat(result.provenance().promptId()).isEqualTo("solver");
        assertThat(new SolutionCandidateValidator().validate(result.rawOutputJson(),source,"LOCAL_FIXTURE").generationOrigin()).isEqualTo("LOCAL_FIXTURE");
    }
}
