package com.verifiedai.integration.solving;

import com.verifiedai.solving.SolverTestData;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.ai.application.*;
import com.verifiedai.ai.infrastructure.provider.SolverFixture;
import com.verifiedai.ai.infrastructure.provider.AiProviderRequest;
import com.verifiedai.problem.application.solving.SolveSource;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class SolverFixtureConfiguration {
    @Bean public Fixture solverFixture() { return new Fixture(); }
    public static class Fixture implements SolverFixture {
        public final AtomicInteger calls=new AtomicInteger();
        public volatile Function<AiProviderRequest,AiSolveResult> behavior=this::success;
        public void reset() { calls.set(0);behavior=this::success; }
        public AiSolveResult execute(AiProviderRequest req) { calls.incrementAndGet();return behavior.apply(req); }
        public AiSolveResult success(AiProviderRequest req) {
            try {
                var in=(AiSolveRequest)req.command().request();var c=new ObjectMapper().readTree(in.canonicalProblemJson());
                var source=new SolveSource(in.canonicalProblemId(),req.command().executionContext().userId(),
                    req.command().executionContext().problemSessionId(),in.problemParseId(),in.problemParseRevision(),1,
                    c.path("problemType").asText(),c.path("taskType").asText(),in.canonicalProblemJson());
                var p=req.routePlan();
                return new AiSolveResult(SolverTestData.candidate(source).toString(),new AiProvenance(req.target().provider(),
                    req.target().model(),p.routePolicyVersion(),p.promptId(),p.promptVersion(),p.schemaVersion(),"fixture-request",null,false),
                    AiUsage.zeroCost(p.pricingVersion()),1);
            } catch(Exception e) { throw new IllegalStateException(e); }
        }
    }
}
