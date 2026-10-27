package com.verifiedai.integration.solving;

import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.problem.application.canonicalization.CanonicalProblemApplicationService;
import com.verifiedai.solving.SolverSourceFixture;
import com.verifiedai.solving.application.*;
import com.verifiedai.solving.application.canonical.*;
import com.verifiedai.solving.application.secondary.*;
import com.verifiedai.solving.domain.model.SolveState;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution.*;
import com.verifiedai.solving.infrastructure.persistence.SecondarySolveStore;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles({"test","solver-fixture"})
@Import(SecondaryFixtureConfiguration.class)
@TestPropertySource(properties={"app.solving.secondary.enabled=true","app.ai.secondary-fixture-enabled=true",
    "app.solving.secondary.sample-basis-points=1000","app.solving.secondary.worker-enabled=false","app.solving.canonical.worker-enabled=false"})
class CanonicalSolutionFixtureE2ETest extends PostgresIntegrationTestSupport {
    @Autowired PrimarySolveService primary;
    @Autowired SecondarySolveService secondary;
    @Autowired CanonicalSolutionService projections;
    @Autowired SecondarySolveStore evidence;
    @Autowired CanonicalProblemApplicationService canonical;
    @Autowired SecondaryFixtureConfiguration.Fixture provider;
    @Autowired JdbcTemplate jdbc;
    SolverSourceFixture fixture;
    UUID user,session;
    @BeforeEach void setup() {
        fixture=new SolverSourceFixture(jdbc);provider.reset();user=fixture.insertUser();
        session=fixture.insertSupportedParse(user,SolverSourceFixture.equationParseJson("x + 1 = 2"),1);
        canonical.canonicalize(user,session);
        var policy=new SecondaryEscalationPolicy(1000);
        // Before any solve references it, select a deterministic synthetic QC fixture identity.
        UUID id=java.util.stream.IntStream.range(0,100000).mapToObj(i->new UUID(session.getMostSignificantBits(),i)).filter(policy::sampled).findFirst().orElseThrow();
        jdbc.update("update canonical_problems set id=? where problem_session_id=?",id,session);
    }
    SolveView completePrimary() {
        var p=primary.start(new SolveCommand(user,session,"canonical-e2e-"+session,null));primary.run(p.jobId());return primary.get(user,session,p.jobId());
    }
    @Test void realPrimaryAndSecondaryFixturePipelinesProduceSeparateDurableUnverifiedProjections() {
        var p=completePrimary();var s=secondary.evaluate(user,session,p.jobId());secondary.run(s.id());
        var agreementBefore=evidence.agreement(s.id()).orElseThrow();
        projections.recover(100);
        var a=projections.materialize(user,session,new Origin(Role.PRIMARY,p.jobId()));
        var b=projections.materialize(user,session,new Origin(Role.SECONDARY,s.id()));
        assertThat(a.state()).isEqualTo(CanonicalSolutions.MaterializationState.REUSED);
        assertThat(b.state()).isEqualTo(CanonicalSolutions.MaterializationState.REUSED);
        assertThat(a.solutionId()).isNotEqualTo(b.solutionId());
        var view=projections.current(user,session);
        assertThat(view.solutions()).hasSize(2);
        var primarySolution=projections.historical(user,session,a.solutionId()).solution();
        var secondarySolution=projections.historical(user,session,b.solutionId()).solution();
        assertThat(primarySolution.finalStatement()).isEqualTo(secondarySolution.finalStatement());
        assertThat(secondarySolution.lineage().parentPrimaryJobId()).isEqualTo(p.jobId());
        assertThat(primarySolution.steps().getFirst().candidateStepId()).isEqualTo("step-1");
        assertThat(secondarySolution.steps().getFirst().candidateStepId()).isEqualTo("secondary-1");
        assertThat(primarySolution.provenance().model()).isNotEqualTo(secondarySolution.provenance().model());
        assertThat(evidence.agreement(s.id()).orElseThrow()).isEqualTo(agreementBefore);
        assertThat(new SolutionCandidateValidator().parse(agreementBefore).path("mathematicalVerificationPerformed").asBoolean()).isFalse();
        projections.recover(100);
        assertThat(primary.get(user,session,p.jobId()).state()).isEqualTo(SolveState.GENERATED_UNVERIFIED);
        assertThat(primary.get(user,session,p.jobId()).verificationNotice()).contains("has not been performed");
        assertThat(provider.primary.calls.get()).isEqualTo(1);assertThat(provider.secondaryCalls.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from ai_usage_records where problem_session_id=?",Integer.class,session)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select coalesce(sum(estimated_cost_micros),0) from ai_usage_records where problem_session_id=?",Long.class,session)).isZero();
    }
    @Test void lateAcceptedPrimaryIsHistoricalAndDoesNotProduceFalseCurrentSuccessOrRetryInference() {
        provider.primary.behavior=req->{
            fixture.insertSupportedParse(user,session,SolverSourceFixture.equationParseJson("x + 2 = 4"),2);canonical.canonicalize(user,session);
            return provider.primary.success(req);
        };
        var p=completePrimary();assertThat(p.state()).isEqualTo(SolveState.STALE);
        var projection=projections.materialize(user,session,new Origin(Role.PRIMARY,p.jobId()));
        assertThat(projections.historical(user,session,projection.solutionId()).state()).isEqualTo(CanonicalSolutions.ReadState.HISTORICAL_SOURCE);
        assertThat(projections.current(user,session).state()).isEqualTo(CanonicalSolutions.ReadState.NOT_MATERIALIZED);
        projections.recover(100);
        assertThat(provider.primary.calls.get()).isEqualTo(1);assertThat(provider.secondaryCalls.get()).isZero();
    }
    @Test void secondaryProjectionOwnerAndParentBindingsAreEnforcedByDatabase() {
        var p=completePrimary();var s=secondary.evaluate(user,session,p.jobId());secondary.run(s.id());
        var id=projections.materialize(user,session,new Origin(Role.SECONDARY,s.id())).solutionId();
        assertThat(jdbc.queryForObject("select primary_job_id from canonical_solutions where id=?",UUID.class,id)).isEqualTo(p.jobId());
        assertThatThrownBy(()->jdbc.update("update canonical_solutions set primary_job_id=? where id=?",UUID.randomUUID(),id))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        var source=projections.historical(user,session,id).solution().lineage().canonicalProblemId();
        jdbc.update("delete from canonical_problems where id=?",source);
        assertThat(jdbc.queryForObject("select count(*) from canonical_solutions where id=?",Integer.class,id)).isZero();
        assertThatThrownBy(()->projections.historical(user,session,id)).isInstanceOf(com.verifiedai.sharedkernel.error.ApiProblemException.class);
    }
}
