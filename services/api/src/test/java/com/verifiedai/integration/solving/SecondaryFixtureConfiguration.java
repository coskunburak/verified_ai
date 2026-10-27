package com.verifiedai.integration.solving;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.ai.application.*;
import com.verifiedai.ai.infrastructure.provider.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Separate synthetic generator. It has no primary result/candidate/source-store dependency. */
@TestConfiguration
public class SecondaryFixtureConfiguration {
    @Bean public Fixture solverFixture() { return new Fixture(); }
    public static class Fixture implements SolverFixture {
        public final SolverFixtureConfiguration.Fixture primary=new SolverFixtureConfiguration.Fixture();
        public final AtomicInteger secondaryCalls=new AtomicInteger();
        public volatile AiSolveRequest secondaryInput;
        public volatile Function<AiProviderRequest,AiSolveResult> secondary=this::independent;
        public void reset() { primary.reset();secondaryCalls.set(0);secondaryInput=null;secondary=this::independent; }
        public AiSolveResult execute(AiProviderRequest request) {
            if(request.command().routeContext().routeRole()!=AiRouteContext.RouteRole.SECONDARY_SOLVER) return primary.execute(request);
            secondaryCalls.incrementAndGet();secondaryInput=(AiSolveRequest)request.command().request();return secondary.apply(request);
        }
        public AiSolveResult independent(AiProviderRequest req) {
            try {
                var m=new ObjectMapper();var input=(AiSolveRequest)req.command().request();var canonical=m.readTree(input.canonicalProblemJson());
                // Deliberately bounded synthetic x=1 fixture: no algebra quality or model independence claim.
                var candidate=m.createObjectNode();candidate.put("schemaVersion","solution-candidate-v1");
                candidate.put("canonicalProblemId",input.canonicalProblemId().toString()).put("problemParseId",input.problemParseId().toString());
                candidate.put("problemParseRevision",input.problemParseRevision()).put("candidateVersion",1).put("generationOrigin","LOCAL_FIXTURE");
                candidate.put("problemType",canonical.path("problemType").asText()).put("taskType",canonical.path("taskType").asText());
                candidate.put("method","ALGEBRAIC_TRANSFORMATION");
                var answer=candidate.putObject("finalAnswer");answer.put("kind","RELATION").put("relation","EQUALS");
                answer.putObject("left").put("kind","VARIABLE").put("symbol","x");
                answer.putObject("right").put("kind","NUMBER").put("numericType","INTEGER").put("value","1");
                var step=candidate.putArray("steps").addObject();step.put("id","secondary-1").put("order",1).put("category","CONCLUDE")
                    .put("explanation","Independent synthetic contract fixture.");step.set("claim",answer.deepCopy());step.putArray("dependsOn");
                for(var pair:new String[][]{{"sourceAssumptionIds","sourceAssumptions"},{"sourceConstraintIds","sourceConstraints"},{"derivedRestrictionIds","derivedRestrictions"}}) {
                    var refs=candidate.putArray(pair[0]);canonical.path(pair[1]).forEach(v->refs.add(v.path("id").asText()));
                }
                candidate.putArray("ambiguities");candidate.putArray("unresolvedIssues");var p=req.routePlan();
                return new AiSolveResult(candidate.toString(),new AiProvenance(req.target().provider(),req.target().model(),p.routePolicyVersion(),
                    p.promptId(),p.promptVersion(),p.schemaVersion(),"secondary-fixture",null,false),AiUsage.zeroCost(p.pricingVersion()),1);
            } catch(Exception e) { throw new IllegalStateException(e); }
        }
    }
}
