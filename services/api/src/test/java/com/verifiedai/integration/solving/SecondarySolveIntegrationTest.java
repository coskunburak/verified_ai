package com.verifiedai.integration.solving;

import com.verifiedai.ai.application.*;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.problem.application.canonicalization.CanonicalProblemApplicationService;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.solving.SolverSourceFixture;
import com.verifiedai.solving.application.*;
import com.verifiedai.solving.application.secondary.*;
import com.verifiedai.solving.domain.model.SolveState;
import com.verifiedai.solving.domain.model.secondary.*;
import com.verifiedai.solving.infrastructure.persistence.SecondarySolveStore;
import com.verifiedai.solving.infrastructure.privacy.SolvingLifecycleContributor;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles({"test","solver-fixture"})
@Import(SecondaryFixtureConfiguration.class)
@TestPropertySource(properties={"app.solving.secondary.enabled=true","app.ai.secondary-fixture-enabled=true",
    "app.solving.secondary.sample-basis-points=1000","app.solving.secondary.worker-enabled=false"})
class SecondarySolveIntegrationTest extends PostgresIntegrationTestSupport {
    @Autowired PrimarySolveService primary;
    @Autowired SecondarySolveService secondary;
    @Autowired CanonicalProblemApplicationService canonical;
    @Autowired SecondarySolveStore store;
    @Autowired JdbcTemplate jdbc;
    @Autowired SecondaryFixtureConfiguration.Fixture provider;
    @Autowired SolvingLifecycleContributor privacy;
    @Autowired TransactionTemplate tx;
    @Autowired tools.jackson.databind.json.JsonMapper routeJson;
    SolverSourceFixture fixture;
    UUID user,session;
    @BeforeEach void setup() {
        fixture=new SolverSourceFixture(jdbc);provider.reset();user=fixture.insertUser();
        session=fixture.insertSupportedParse(user,SolverSourceFixture.equationParseJson("x + 1 = 2"),1);
        canonical.canonicalize(user,session);selectQcBucket(true);
    }
    void selectQcBucket(boolean sampled) {
        var policy=new SecondaryEscalationPolicy(1000);
        // Synthetic database identity chosen before any solve references it; no production source mutation.
        UUID id=java.util.stream.IntStream.range(0,100000).mapToObj(i->new UUID(session.getMostSignificantBits(),i))
            .filter(v->policy.sampled(v)==sampled).findFirst().orElseThrow();
        jdbc.update("update canonical_problems set id=? where problem_session_id=?",id,session);
    }
    SolveView completePrimary() {
        var p=primary.start(new SolveCommand(user,session,"primary-"+session,null));primary.run(p.jobId());return primary.get(user,session,p.jobId());
    }
    SecondaryJob queued() { var p=completePrimary();return secondary.evaluate(user,session,p.jobId()); }
    @Test void fixtureEndToEndPersistsAgreementAndSeparateLedgerWhileClientRemainsUnverified() throws Exception {
        var p=completePrimary();
        assertThat(store.undecided(secondary.policyVersion(),10000)).contains(p.jobId());
        var scheduled=secondary.evaluate(user,session,p.jobId());secondary.run(scheduled.id());
        var j=store.forPrimary(p.jobId(),secondary.policyVersion()).orElseThrow();
        assertThat(store.undecided(secondary.policyVersion(),10000)).doesNotContain(p.jobId());
        assertThat(j.state()).isEqualTo(SecondaryState.GENERATED_UNVERIFIED);assertThat(j.attempt()).isEqualTo(1);
        var pinnedRoute=routeJson.readValue(j.routeJson(),AiRoutePlan.class);
        assertThat(pinnedRoute.primary().model()).isEqualTo("local-fixture-secondary-solver-v1");
        assertThat(pinnedRoute.promptId()).isEqualTo("secondary-solver");
        assertThat(pinnedRoute.timeout()).isEqualTo(java.time.Duration.ofSeconds(30));
        assertThat(secondary.get(user,session,j.id()).agreement().path("outcome").asText()).isEqualTo("AGREEMENT");
        assertThat(secondary.get(user,session,j.id()).agreement().path("mathematicalVerificationPerformed").asBoolean()).isFalse();
        assertThat(primary.get(user,session,p.jobId()).state()).isEqualTo(SolveState.GENERATED_UNVERIFIED);
        assertThat(provider.secondaryCalls.get()).isEqualTo(1);
        assertThat(provider.secondaryInput.canonicalProblemJson()).doesNotContain("finalAnswer","steps","method","explanation");
        assertThat(jdbc.queryForObject("select count(*) from ai_usage_records where problem_session_id=? and capability='SOLVE'",Integer.class,session)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(distinct model) from ai_usage_records where problem_session_id=?",Integer.class,session)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from ai_spend_attempts where operation_id=?",Integer.class,j.operationId())).isZero();
    }
    @Test void lowRiskSkipIsDurableAndMakesNoSecondaryCall() {
        selectQcBucket(false);var p=completePrimary();var j=secondary.evaluate(user,session,p.jobId());
        assertThat(j.state()).isEqualTo(SecondaryState.NOT_REQUIRED);secondary.run(j.id());
        assertThat(provider.secondaryCalls.get()).isZero();assertThat(store.agreement(j.id())).isEmpty();
    }
    @Test void primaryIssueEscalatesOutsideQcWithoutLeakingTheIssue() {
        selectQcBucket(false);provider.primary.behavior=req->{var result=provider.primary.success(req);
            var json=new SolutionCandidateValidator().parse(result.rawOutputJson());
            ((com.fasterxml.jackson.databind.node.ArrayNode)json.path("unresolvedIssues")).add("PRIMARY_PRIVATE_SENTINEL");
            return new AiSolveResult(json.toString(),result.provenance(),result.usage(),1);};
        var j=queued();assertThat(j.reason()).isEqualTo("PRIMARY_UNRESOLVED");secondary.run(j.id());
        assertThat(provider.secondaryInput.canonicalProblemJson()).doesNotContain("PRIMARY_PRIVATE_SENTINEL");
        assertThat(secondary.get(user,session,j.id()).agreement().path("outcome").asText()).isEqualTo("INDETERMINATE");
    }
    @Test void duplicateTriggerAndWorkersAreIdempotent() throws Exception {
        var p=completePrimary();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->secondary.evaluate(user,session,p.jobId()));var b=pool.submit(()->secondary.evaluate(user,session,p.jobId()));
            var j=a.get(10,TimeUnit.SECONDS);assertThat(b.get(10,TimeUnit.SECONDS).id()).isEqualTo(j.id());
            var ra=pool.submit(()->secondary.run(j.id()));var rb=pool.submit(()->secondary.run(j.id()));
            ra.get(10,TimeUnit.SECONDS);rb.get(10,TimeUnit.SECONDS);assertThat(provider.secondaryCalls.get()).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from secondary_solve_jobs where primary_job_id=?",Integer.class,p.jobId())).isEqualTo(1);
        }
    }
    @Test void unknownRecoveryNeverReplaysAndEnforcesOneAttempt() {
        var j=queued();tx.executeWithoutResult(s->store.claim(j.id(),Instant.now(),2));
        store.recover(Instant.now().plusSeconds(120));secondary.run(j.id());
        assertThat(store.get(j.id()).orElseThrow().state()).isEqualTo(SecondaryState.OUTCOME_UNKNOWN);
        assertThat(provider.secondaryCalls.get()).isZero();
        Boolean reclaimed=tx.execute(s->store.claim(j.id(),Instant.now(),2));
        assertThat(reclaimed).isFalse();
    }
    @Test void timeoutRetainsLedgerAndUnknownOutcomeWithoutRetry() {
        var j=queued();provider.secondary=req->{throw new AiProviderException(AiProviderFailureClass.TIMEOUT,true,"synthetic timeout");};
        secondary.run(j.id());secondary.run(j.id());
        assertThat(store.get(j.id()).orElseThrow().state()).isEqualTo(SecondaryState.OUTCOME_UNKNOWN);
        assertThat(store.get(j.id()).orElseThrow().ledgerRecordId()).isNotNull();assertThat(provider.secondaryCalls.get()).isEqualTo(1);
    }
    @Test void sourceStaleDuringExecutionCannotPromoteLateAgreement() {
        var j=queued();provider.secondary=req->{
            fixture.insertSupportedParse(user,session,SolverSourceFixture.equationParseJson("x + 2 = 4"),2);
            canonical.canonicalize(user,session);return provider.independent(req);};
        secondary.run(j.id());assertThat(store.get(j.id()).orElseThrow().state()).isEqualTo(SecondaryState.STALE);
        assertThat(store.agreement(j.id())).isEmpty();assertThat(secondary.get(user,session,j.id()).agreement()).isNull();
    }
    @Test void sourceStaleBeforeDispatchBlocksInference() {
        var j=queued();jdbc.update("update problem_parses set review_required=true where problem_session_id=?",session);
        secondary.run(j.id());assertThat(store.get(j.id()).orElseThrow().state()).isEqualTo(SecondaryState.POLICY_BLOCKED);
        assertThat(provider.secondaryCalls.get()).isZero();
    }
    @Test void lateCancellationFencesEvidenceAndOwnershipIsRequired() {
        var j=queued();var other=fixture.insertUser();
        assertThatThrownBy(()->secondary.get(other,session,j.id())).isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(()->secondary.cancel(other,session,j.id())).isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(()->secondary.evaluate(other,session,j.primaryJobId())).isInstanceOf(ApiProblemException.class);
        provider.secondary=req->{secondary.cancel(user,session,j.id());return provider.independent(req);};secondary.run(j.id());
        assertThat(store.get(j.id()).orElseThrow().state()).isEqualTo(SecondaryState.CANCELLED);assertThat(store.agreement(j.id())).isEmpty();
    }
    @Test void malformedOutputIsTerminalWithLedgerButNoAgreement() {
        var j=queued();provider.secondary=req->{var r=provider.independent(req);return new AiSolveResult("{}",r.provenance(),r.usage(),1);};
        secondary.run(j.id());assertThat(store.get(j.id()).orElseThrow().state()).isEqualTo(SecondaryState.TERMINAL_FAILURE);
        assertThat(store.get(j.id()).orElseThrow().ledgerRecordId()).isNotNull();assertThat(store.agreement(j.id())).isEmpty();
    }
    @Test void globalAndPerOwnerConcurrencyBoundsHoldAcrossClaims() {
        var first=queued();
        session=fixture.insertSupportedParse(user,SolverSourceFixture.equationParseJson("x + 1 = 2"),1);
        canonical.canonicalize(user,session);selectQcBucket(true);var sameOwner=queued();
        Boolean firstClaim=tx.execute(s->store.claim(first.id(),Instant.now(),2));
        assertThat(firstClaim).isTrue();
        Boolean sameOwnerClaim=tx.execute(s->store.claim(sameOwner.id(),Instant.now(),2));
        assertThat(sameOwnerClaim).isFalse();
        user=fixture.insertUser();session=fixture.insertSupportedParse(user,SolverSourceFixture.equationParseJson("x + 1 = 2"),1);
        canonical.canonicalize(user,session);selectQcBucket(true);var second=queued();
        Boolean secondClaim=tx.execute(s->store.claim(second.id(),Instant.now(),2));
        assertThat(secondClaim).isTrue();
        user=fixture.insertUser();session=fixture.insertSupportedParse(user,SolverSourceFixture.equationParseJson("x + 1 = 2"),1);
        canonical.canonicalize(user,session);selectQcBucket(true);var third=queued();
        Boolean thirdClaim=tx.execute(s->store.claim(third.id(),Instant.now(),2));
        assertThat(thirdClaim).isFalse();
        store.recover(Instant.now().plusSeconds(120));
    }
    @Test void databaseConstraintsOwnershipImmutabilityExportAndDeletion() {
        var j=queued();secondary.run(j.id());
        assertThat(privacy.exportUserData(user).toString()).contains("blind-agreement-v1","secondaryJobs");
        assertThatThrownBy(()->jdbc.update("update secondary_solution_evidence set agreement_jsonb=agreement_jsonb where job_id=?",j.id()))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("update secondary_solve_jobs set user_id=? where id=?",fixture.insertUser(),j.id()))
            .isInstanceOf(org.springframework.jdbc.UncategorizedSQLException.class)
            .hasMessageContaining("Secondary source, policy and route are immutable");
        assertThatThrownBy(()->jdbc.update("update secondary_solve_jobs set attempt_number=2 where id=?",j.id()))
            .isInstanceOf(org.springframework.jdbc.UncategorizedSQLException.class)
            .hasMessageContaining("Secondary attempt cannot be replayed");
        var unchanged=store.get(j.id()).orElseThrow();
        assertThat(unchanged.userId()).isEqualTo(user);
        assertThat(unchanged.attempt()).isEqualTo(1);
        assertThatThrownBy(()->jdbc.update("update secondary_solve_jobs set state='QUEUED',completed_at=null where id=?",j.id()))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        privacy.deleteUserData(user,Instant.now());assertThat(store.get(j.id())).isEmpty();assertThat(store.agreement(j.id())).isEmpty();
    }
    @Test void databaseFailureAfterInferenceRecoversUnknownWithoutReplay() {
        var j=queued();jdbc.execute("""
            create function reject_secondary_fixture_evidence() returns trigger language plpgsql as $$
            begin raise exception 'synthetic persistence failure'; end; $$
            """);
        jdbc.execute("create trigger reject_secondary_fixture before insert on secondary_solution_evidence for each row execute function reject_secondary_fixture_evidence()");
        try {
            assertThatThrownBy(()->secondary.run(j.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(store.get(j.id()).orElseThrow().state()).isEqualTo(SecondaryState.RUNNING);
        } finally {
            jdbc.execute("drop trigger reject_secondary_fixture on secondary_solution_evidence");
            jdbc.execute("drop function reject_secondary_fixture_evidence()");
        }
        store.recover(Instant.now().plusSeconds(120));secondary.run(j.id());
        assertThat(store.get(j.id()).orElseThrow().state()).isEqualTo(SecondaryState.OUTCOME_UNKNOWN);
        assertThat(provider.secondaryCalls.get()).isEqualTo(1);
    }
    @Test void providerCallSuspendsCallerTransaction() {
        var j=queued();provider.secondary=req->{
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return provider.independent(req);};
        tx.executeWithoutResult(s->secondary.run(j.id()));assertThat(store.get(j.id()).orElseThrow().state()).isEqualTo(SecondaryState.GENERATED_UNVERIFIED);
    }
}
