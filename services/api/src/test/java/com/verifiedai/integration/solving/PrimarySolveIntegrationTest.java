package com.verifiedai.integration.solving;

import com.verifiedai.solving.application.*;

import com.verifiedai.ai.application.*;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.problem.application.canonicalization.CanonicalProblemApplicationService;
import com.verifiedai.solving.SolverSourceFixture;

import com.verifiedai.solving.domain.model.SolveState;
import com.verifiedai.solving.infrastructure.persistence.SolveStore;
import com.verifiedai.solving.infrastructure.privacy.SolvingLifecycleContributor;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles({"test","solver-fixture"})
@Import(SolverFixtureConfiguration.class)
class PrimarySolveIntegrationTest extends PostgresIntegrationTestSupport {
    @Autowired PrimarySolveService service;
    @Autowired CanonicalProblemApplicationService canonical;
    @Autowired JdbcTemplate jdbc;
    @Autowired SolverFixtureConfiguration.Fixture provider;
    @Autowired SolvingLifecycleContributor privacy;
    @Autowired SolveStore store;
    @Autowired org.springframework.transaction.support.TransactionTemplate transaction;
    SolverSourceFixture fixture;
    UUID user, session;
    @BeforeEach void setup() {
        // Full lineage rows are isolated in Testcontainers; each test gets a different user/session.
        fixture=new SolverSourceFixture(jdbc);provider.reset();
        user=fixture.insertUser();session=fixture.insertSupportedParse(user,SolverSourceFixture.equationParseJson("x + 1 = 2"),1);
        canonical.canonicalize(user,session);
    }
    SolveView start() { return service.start(new SolveCommand(user,session,"request-"+session,null)); }
    @Test void duplicateRequestProducesOneDurableUnverifiedCandidateAndLedgerOperation() {
        var a=start();var b=start();assertThat(b.jobId()).isEqualTo(a.jobId());
        provider.behavior=req->{
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return provider.success(req);
        };
        service.run(a.jobId());service.run(a.jobId());
        var result=service.get(user,session,a.jobId());
        assertThat(result.state()).isEqualTo(SolveState.GENERATED_UNVERIFIED);
        assertThat(result.candidate()).isNotNull();assertThat(provider.calls.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from ai_usage_records where operation_id=(select operation_id from solve_jobs where id=?)",Integer.class,a.jobId())).isEqualTo(1);
        assertThat(result.verificationNotice()).contains("has not been performed");
    }
    @Test void selectedParseChangeDuringInferencePreventsCurrentPromotion() {
        var job=start();
        provider.behavior=req->{
            fixture.insertSupportedParse(user,session,SolverSourceFixture.equationParseJson("x + 2 = 4"),2);
            canonical.canonicalize(user,session);
            return provider.success(req);
        };
        service.run(job.jobId());
        assertThat(service.get(user,session,job.jobId()).state()).isEqualTo(SolveState.STALE);
        assertThat(store.candidate(job.jobId())).isPresent();
    }
    @Test void parseChangeBeforeDispatchBlocksProvider() {
        var job=start();fixture.insertSupportedParse(user,session,SolverSourceFixture.equationParseJson("x + 2 = 4"),2);
        canonical.canonicalize(user,session);service.run(job.jobId());
        assertThat(provider.calls.get()).isZero();assertThat(service.get(user,session,job.jobId()).state()).isEqualTo(SolveState.STALE);
    }
    @Test void crossUserAccessAndInactiveAccountsAreBlocked() {
        var job=start();UUID other=fixture.insertUser();
        assertThatThrownBy(()->service.get(other,session,job.jobId())).isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(()->service.cancel(other,session,job.jobId())).isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(()->service.start(new SolveCommand(other,session,"foreign-request",null))).isInstanceOf(ApiProblemException.class);
        jdbc.update("update users set status='DELETION_REQUESTED' where id=?",user);
        assertThatThrownBy(()->service.get(user,session,job.jobId())).isInstanceOf(ApiProblemException.class);
    }
    @Test void reviewRequiredSourceDoesNotDispatch() {
        jdbc.update("update problem_parses set review_required=true where problem_session_id=?",session);
        assertThatThrownBy(this::start).isInstanceOf(ApiProblemException.class);assertThat(provider.calls.get()).isZero();
    }
    @Test void sourceBecomingReviewRequiredCommitsBlockedStateWithoutRollbackOnly() {
        var job=start();
        jdbc.update("update problem_parses set review_required=true where problem_session_id=?",session);
        service.run(job.jobId());
        assertThat(store.get(job.jobId()).orElseThrow().state()).isEqualTo(SolveState.POLICY_BLOCKED);
        assertThat(service.get(user,session,job.jobId()).state()).isEqualTo(SolveState.STALE);
        assertThat(provider.calls.get()).isZero();
    }
    @Test void missingSourceAtCompletionCommitsStaleArtifact() {
        var job=start();
        provider.behavior=req->{
            jdbc.update("update problem_parses set review_required=true where problem_session_id=?",session);
            return provider.success(req);
        };
        service.run(job.jobId());
        assertThat(store.get(job.jobId()).orElseThrow().state()).isEqualTo(SolveState.STALE);
        assertThat(service.get(user,session,job.jobId()).state()).isEqualTo(SolveState.STALE);
        assertThat(store.candidate(job.jobId())).isPresent();
    }
    @Test void workerSuspendsCallerTransactionAcrossProviderDispatch() {
        var job=start();
        provider.behavior=req->{
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return provider.success(req);
        };
        transaction.executeWithoutResult(status->service.run(job.jobId()));
        assertThat(service.get(user,session,job.jobId()).state()).isEqualTo(SolveState.GENERATED_UNVERIFIED);
        assertThat(provider.calls.get()).isEqualTo(1);
    }
    @Test void cancellationFencesLateCompletion() {
        var job=start();provider.behavior=req->{service.cancel(user,session,job.jobId());return provider.success(req);};
        service.run(job.jobId());assertThat(service.get(user,session,job.jobId()).state()).isEqualTo(SolveState.CANCELLED);
        assertThat(store.candidate(job.jobId())).isEmpty();
    }
    @Test void unknownOutcomeRecoveryNeverReexecutesProvider() {
        var job=start();store.claim(job.jobId(),Instant.now());
        store.recover(Instant.now().plusSeconds(120));service.run(job.jobId());
        assertThat(service.get(user,session,job.jobId()).state()).isEqualTo(SolveState.OUTCOME_UNKNOWN);
        assertThat(provider.calls.get()).isZero();
        assertThatThrownBy(()->service.start(new SolveCommand(user,session,"retry-unknown",job.jobId()))).isInstanceOf(ApiProblemException.class);
    }
    @Test void timeoutDoesNotAutomaticallyRetryOrBecomeFreeConfirmedSuccess() {
        var job=start();provider.behavior=req->{throw new AiProviderException(AiProviderFailureClass.TIMEOUT,true,"fixture timeout");};
        service.run(job.jobId());service.run(job.jobId());
        assertThat(service.get(user,session,job.jobId()).state()).isEqualTo(SolveState.OUTCOME_UNKNOWN);
        assertThat(provider.calls.get()).isEqualTo(1);
        assertThat(store.candidate(job.jobId())).isEmpty();
    }
    @Test void explicitRetryUsesNewImmutableJobAndStopsAtThreeRuns() {
        provider.behavior=req->{throw new AiProviderException(AiProviderFailureClass.RATE_LIMITED,true,"fixture rate limit");};
        var job=start();service.run(job.jobId());var first=job.jobId();
        for(int i=2;i<=3;i++) {
            job=service.start(new SolveCommand(user,session,"retry-key-"+i,job.jobId()));service.run(job.jobId());
        }
        UUID last=job.jobId();
        assertThatThrownBy(()->service.start(new SolveCommand(user,session,"retry-key-4",last))).isInstanceOf(ApiProblemException.class);
        assertThat(provider.calls.get()).isEqualTo(3);
        assertThat(store.get(first).orElseThrow().state()).isEqualTo(SolveState.RETRYABLE_FAILURE);
    }
    @Test void malformedCandidateFailsWithoutErasingGatewayUsage() {
        var job=start();provider.behavior=req->{var good=provider.success(req);return new AiSolveResult("{}",good.provenance(),good.usage(),1);};
        service.run(job.jobId());assertThat(service.get(user,session,job.jobId()).state()).isEqualTo(SolveState.TERMINAL_FAILURE);
        assertThat(store.get(job.jobId()).orElseThrow().ledgerRecordId()).isNotNull();
        assertThat(store.candidate(job.jobId())).isEmpty();
    }
    @Test void exportAndDeletionReachDerivedContentAndCandidatesCannotBeUpdated() {
        var job=start();service.run(job.jobId());
        assertThat(privacy.exportUserData(user).toString()).contains("solution-candidate-v1").doesNotContain("providerResponseId");
        assertThatThrownBy(()->jdbc.update("update solution_candidates set artifact_jsonb=artifact_jsonb where job_id=?",job.jobId()))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        privacy.deleteUserData(user,Instant.now());assertThat(store.get(job.jobId())).isEmpty();assertThat(store.candidate(job.jobId())).isEmpty();
    }
    @Test void concurrentRequestsAndWorkersDispatchOnce() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            CountDownLatch go=new CountDownLatch(1);
            var a=pool.submit(()->{go.await();return start();});var b=pool.submit(()->{go.await();return start();});go.countDown();
            var one=a.get(10,TimeUnit.SECONDS);assertThat(b.get(10,TimeUnit.SECONDS).jobId()).isEqualTo(one.jobId());
            var runA=pool.submit(()->service.run(one.jobId()));var runB=pool.submit(()->service.run(one.jobId()));
            runA.get(10,TimeUnit.SECONDS);runB.get(10,TimeUnit.SECONDS);assertThat(provider.calls.get()).isEqualTo(1);
        }
    }
    @Test void persistenceFailureAfterProviderSuccessRecoversWithoutReplay() {
        var job=start();
        jdbc.execute("""
            create function reject_solver_test_candidate() returns trigger language plpgsql as $$
            begin raise exception 'simulated candidate persistence failure'; end; $$
            """);
        jdbc.execute("create trigger reject_solver_test_candidate before insert on solution_candidates for each row execute function reject_solver_test_candidate()");
        try {
            assertThatThrownBy(()->service.run(job.jobId())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        } finally {
            jdbc.execute("drop trigger reject_solver_test_candidate on solution_candidates");
            jdbc.execute("drop function reject_solver_test_candidate()");
        }
        assertThat(store.get(job.jobId()).orElseThrow().state()).isEqualTo(SolveState.RUNNING);
        assertThat(store.candidate(job.jobId())).isEmpty();
        assertThat(jdbc.queryForObject("select status from ai_usage_records where operation_id=(select operation_id from solve_jobs where id=?)",String.class,job.jobId()))
            .isEqualTo("SUCCEEDED");
        store.recover(Instant.now().plusSeconds(120));service.run(job.jobId());
        assertThat(store.get(job.jobId()).orElseThrow().state()).isEqualTo(SolveState.OUTCOME_UNKNOWN);
        assertThat(provider.calls.get()).isEqualTo(1);
    }
    @Test void changingIdempotencySourceAndCreatingParallelKeysCannotStartNewCalls() {
        var job=start();
        assertThatThrownBy(()->service.start(new SolveCommand(user,session,"another-key",null))).isInstanceOf(ApiProblemException.class);
        fixture.insertSupportedParse(user,session,SolverSourceFixture.equationParseJson("x + 2 = 4"),2);
        canonical.canonicalize(user,session);
        assertThatThrownBy(this::start).isInstanceOf(ApiProblemException.class);
        assertThat(provider.calls.get()).isZero();
    }
    @Test void sourceOwnershipForeignKeyRejectsCrossUserBinding() {
        var job=start();UUID other=fixture.insertUser();
        assertThatThrownBy(()->jdbc.update("update solve_jobs set user_id=? where id=?",other,job.jobId()))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
