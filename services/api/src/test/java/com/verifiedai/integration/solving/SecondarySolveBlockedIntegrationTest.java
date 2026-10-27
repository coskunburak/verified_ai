package com.verifiedai.integration.solving;

import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.problem.application.canonicalization.CanonicalProblemApplicationService;
import com.verifiedai.solving.SolverSourceFixture;
import com.verifiedai.solving.application.*;
import com.verifiedai.solving.application.secondary.SecondarySolveService;
import com.verifiedai.solving.domain.model.secondary.SecondaryState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles({"test","solver-fixture"})
@Import(SolverFixtureConfiguration.class)
@TestPropertySource(properties={"app.solving.secondary.enabled=true","app.ai.secondary-fixture-enabled=false"})
class SecondarySolveBlockedIntegrationTest extends PostgresIntegrationTestSupport {
    @Autowired PrimarySolveService primary;
    @Autowired SecondarySolveService secondary;
    @Autowired CanonicalProblemApplicationService canonical;
    @Autowired JdbcTemplate jdbc;
    @Autowired SolverFixtureConfiguration.Fixture provider;
    @Test void unapprovedRealModelCannotBeInferredFromExistingPrimaryAdapter() {
        var fixture=new SolverSourceFixture(jdbc);var user=fixture.insertUser();
        var session=fixture.insertSupportedParse(user,SolverSourceFixture.equationParseJson("x + 1 = 2"),1);
        canonical.canonicalize(user,session);provider.reset();
        provider.behavior=req->{var r=provider.success(req);var root=new SolutionCandidateValidator().parse(r.rawOutputJson());
            ((com.fasterxml.jackson.databind.node.ArrayNode)root.path("unresolvedIssues")).add("Missing premise");
            return new com.verifiedai.ai.application.AiSolveResult(root.toString(),r.provenance(),r.usage(),1);};
        var p=primary.start(new SolveCommand(user,session,"blocked-"+session,null));primary.run(p.jobId());
        var j=secondary.evaluate(user,session,p.jobId());secondary.run(j.id());
        assertThat(j.state()).isEqualTo(SecondaryState.POLICY_BLOCKED);assertThat(j.reason()).isEqualTo("NO_APPROVED_INDEPENDENT_MODEL");
        assertThat(provider.calls.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from ai_usage_records where problem_session_id=?",Integer.class,session)).isEqualTo(1);
    }
}
