package com.verifiedai.integration.verification;

import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.identity.application.AccountPrivacyApplicationService;
import com.verifiedai.problem.application.canonicalization.CanonicalProblemApplicationService;
import com.verifiedai.problem.application.solving.*;
import com.verifiedai.problem.domain.port.ProblemAssetStorage;
import com.verifiedai.solving.*;
import com.verifiedai.solving.application.canonical.*;
import com.verifiedai.solving.domain.model.SolveState;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution.*;
import com.verifiedai.solving.infrastructure.persistence.SolveStore;
import com.verifiedai.verification.application.*;
import com.verifiedai.verification.domain.model.*;
import com.verifiedai.verification.domain.model.MathematicalExecution.*;
import com.verifiedai.verification.infrastructure.privacy.VerificationLifecycleContributor;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@TestPropertySource(properties = {"app.verification.arbitration-enabled=true", "app.verification.arbitration-owner-accepted=true", "app.verification.execution-enabled=true", "app.verification.owner-accepted=true",
    "app.verification.methods=DOMAIN_CONSTRAINT_CHECK,EQUATION_SUBSTITUTION,SOLUTION_SET_COMPLETENESS"})
@Import(VerificationDecisionIntegrationTest.Configuration.class)
class VerificationDecisionIntegrationTest extends PostgresIntegrationTestSupport {
    @TestConfiguration static class Configuration {
        @Bean @Primary ControlledClock policyClock() { return new ControlledClock(); }
        @Bean @Primary ControlledExecutor controlledExecutor() { return new ControlledExecutor(); }
        // Ingestion fixtures contain synthetic object metadata; exercise real account/DB deletion without external S3.
        @Bean @Primary ProblemAssetStorage syntheticAssetStorage() {
            return org.mockito.Mockito.mock(ProblemAssetStorage.class);
        }
    }
    static class ControlledClock extends java.time.Clock {
        volatile CountDownLatch entered, release;
        public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
        public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
        public Instant instant() {
            if (entered != null && Thread.currentThread().getName().equals("policy-evaluation")) {
                entered.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Evaluation barrier timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }
            return Instant.now();
        }
    }
    static class ControlledExecutor implements MathematicalExecutor {
        volatile CountDownLatch entered, release;
        public Result execute(Request request) {
            if (entered != null) {
                entered.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test release timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }
            return Result.failed(request, Reason.TIMEOUT);
        }
    }
    @Autowired VerificationExecution execution;
    @Autowired CanonicalSolutions solutions;
    @Autowired CanonicalProblemApplicationService canonical;
    @Autowired SolveSourceAccess sources;
    @Autowired SolveStore accepted;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired ControlledExecutor controlled;
    @Autowired VerificationLifecycleContributor privacy;
    @Autowired CompositeVerification composite;
    @Autowired AccountPrivacyApplicationService accountPrivacy;
    @Autowired ProblemAssetStorage assetStorage;
    @Autowired com.verifiedai.verification.domain.repository.VerificationRunRepository runs;
    UUID user, session, solutionId, key;
    SolverSourceFixture fixture;
    @BeforeEach void setup() {
        controlled.entered = null; controlled.release = null;
        fixture = new SolverSourceFixture(jdbc); user = fixture.insertUser();
        session = fixture.insertSupportedParse(user, SolverSourceFixture.equationParseJson("x + 1 = 2"), 1);
        canonical.canonicalize(user, session); solutionId = materialize(); key = UUID.randomUUID();
    }
    UUID materialize() {
        UUID job = tx.execute(status -> {
            var source = sources.current(user, session); var now = Instant.now();
            var previous = accepted.latest(source.canonicalProblemId());
            int attempt = previous.map(prior -> prior.attempt() + 1).orElse(1);
            var row = accepted.insert(source, "execution-" + UUID.randomUUID(), previous.map(prior -> prior.id()).orElse(null),
                attempt, now, now.plusSeconds(60));
            accepted.claim(row.id(), now); accepted.finish(row.id(), SolveState.GENERATED_UNVERIFIED, null, null, now);
            accepted.candidate(row.id(), SolverTestData.candidate(source).toString(), "{\"provider\":\"LOCAL_FIXTURE\",\"model\":\"synthetic\"}", now);
            return row.id();
        });
        return solutions.materialize(user, session, new Origin(Role.PRIMARY, job)).solutionId();
    }
    VerificationExecution.Publication run() { return execution.execute(user, session, solutionId, key); }
    int count() { return jdbc.queryForObject("select count(*) from verification_runs where user_id=?", Integer.class, user); }
    @Autowired ControlledClock policyClock;
    @Autowired VerificationArbitrationService arbitration;
    @Autowired com.verifiedai.verification.domain.repository.VerificationDecisionRepository decisions;
    UUID job() { return solutions.historical(user, session, solutionId).solution().lineage().origin().jobId(); }
    VerificationDecision decide(UUID request) { return arbitration.evaluate(user, session, solutionId, key, request); }
    int decisionCount() { return jdbc.queryForObject("select count(*) from verification_decisions where user_id=?", Integer.class, user); }
    @Test void publicationIsIdempotentReadOnlyOnRecoveryAndPreservesGenerationState() {
        run(); var request = UUID.randomUUID(); var d = decide(request);
        assertThat(d.status()).isEqualTo(VerificationDecision.Status.UNVERIFIED);
        assertThat(decide(request)).isEqualTo(d); assertThat(decisionCount()).isEqualTo(1);
        int runCount = count();
        assertThat(arbitration.currentForJob(user, session, job())).contains(d);
        assertThat(arbitration.currentForJob(user, session, job())).contains(d);
        assertThat(count()).isEqualTo(runCount);
        assertThat(jdbc.queryForObject("select count(*) from secondary_solve_jobs where user_id=?", Integer.class, user)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from ai_usage_records where user_id=?", Integer.class, user)).isZero();
        assertThat(solutions.historical(user, session, solutionId).state()).isEqualTo(CanonicalSolutions.ReadState.GENERATED_UNVERIFIED);
        assertThat(privacy.exportUserData(user).get("decisions")).asList().hasSize(1);
    }
    @Test void newRunInvalidatesThePreviousCurrentDecisionAndKeyCannotChangeScope() {
        run(); var request = UUID.randomUUID(); var d = decide(request);
        key = UUID.randomUUID(); run();
        assertThat(arbitration.currentForJob(user, session, job())).isEmpty();
        assertThatThrownBy(() -> decide(request)).isInstanceOf(com.verifiedai.sharedkernel.error.ApiProblemException.class);
        Optional<VerificationDecision> stored = tx.execute(s -> decisions.find(user, session, request));
        assertThat(stored).contains(d);
        assertThat(decisionCount()).isEqualTo(1);
    }
    @Test void currentReadRejectsSourceCorrectionSolutionReplacementAndCrossOwner() {
        run(); var d = decide(UUID.randomUUID()); var originalJob = job();
        var other = fixture.insertUser();
        assertThatThrownBy(() -> arbitration.currentForJob(other, session, originalJob)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> arbitration.evaluate(user, UUID.randomUUID(), solutionId, key, UUID.randomUUID())).isInstanceOf(RuntimeException.class);
        materialize();
        assertThat(arbitration.currentForJob(user, session, originalJob)).isEmpty();
        fixture.insertSupportedParse(user, session, SolverSourceFixture.equationParseJson("x + 2 = 4"), 2);
        canonical.canonicalize(user, session);
        assertThat(arbitration.currentForJob(user, session, originalJob)).isEmpty();
        Optional<VerificationDecision> stored = tx.execute(s -> decisions.find(user, session, d.requestKey()));
        assertThat(stored).contains(d);
    }
    @Test void concurrentPublicationHasOneImmutableWinner() throws Exception {
        run(); var request = UUID.randomUUID(); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { start.await(); return decide(request); });
            var b = pool.submit(() -> { start.await(); return decide(request); }); start.countDown();
            assertThat(a.get(20, TimeUnit.SECONDS)).isEqualTo(b.get(20, TimeUnit.SECONDS));
        }
        assertThat(decisionCount()).isEqualTo(1);
    }
    @Test void immutableRowsAndOwnedCascadeSupportRollbackExportAndAccountDeletion() {
        run(); var d = decide(UUID.randomUUID());
        assertThatThrownBy(() -> jdbc.update("update verification_decisions set policy_version=policy_version where id=?", d.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from verification_decisions where id=?", d.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            jdbc.update("delete from problem_sessions where id=?", session); throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(decisionCount()).isEqualTo(1);
        var export = accountPrivacy.requestExport(user);
        var exported = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(accountPrivacy.downloadExport(user, export.exportId()));
        assertThat(exported.path("verificationEvidence").path("decisions").size()).isEqualTo(1);
        accountPrivacy.requestDeletion(user); accountPrivacy.confirmDeletion(user, "DELETE");
        assertThat(decisionCount()).isZero(); assertThat(count()).isZero();
        assertThatThrownBy(() -> decide(UUID.randomUUID())).isInstanceOf(RuntimeException.class);
    }
    @Test void foreignKeyAndInsertTriggerRejectForgedDecisionProvenance() {
        run(); var d = decide(UUID.randomUUID());
        for (String field : List.of("runId", "source", "evidenceIds", "solutionVersion", "planId")) {
            assertThatThrownBy(() -> jdbc.update("""
                with new_keys as (select gen_random_uuid() as new_id,gen_random_uuid() as new_key)
                insert into verification_decisions(id,request_key,user_id,problem_session_id,solution_id,run_id,policy_version,decision_jsonb,created_at)
                select new_id,new_key,user_id,problem_session_id,solution_id,run_id,policy_version,
                    jsonb_set(jsonb_set(decision_jsonb - ?, '{id}', to_jsonb(new_id::text)),
                        '{requestKey}',to_jsonb(new_key::text)),created_at from verification_decisions,new_keys where id=?
                """, field, d.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        }
        assertThat(decisionCount()).isEqualTo(1);
    }
    @Test void callerTransactionCannotInvertOwnerLockOrder() {
        assertThatThrownBy(() -> tx.execute(s -> arbitration.currentForJob(user, session, UUID.randomUUID())))
            .isInstanceOf(IllegalTransactionStateException.class);
    }
    @Test void selectedParseCorrectionDuringEvaluationRejectsPublicationWithoutDeadlock() throws Exception {
        race(() -> {
            fixture.insertSupportedParse(user, session, SolverSourceFixture.equationParseJson("x + 2 = 4"), 2);
            canonical.canonicalize(user, session);
        });
    }
    @Test void accountDeletionDuringEvaluationRejectsPublicationWithoutDeadlock() throws Exception {
        race(() -> { accountPrivacy.requestDeletion(user); accountPrivacy.confirmDeletion(user, "DELETE"); });
    }
    @Test void solutionReplacementDuringEvaluationRejectsPublicationWithoutDeadlock() throws Exception {
        race(this::materialize);
    }
    void race(Runnable mutation) throws Exception {
        run(); policyClock.entered = new CountDownLatch(1); policyClock.release = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor(r -> new Thread(r, "policy-evaluation"))) {
            var pending = pool.submit(() -> decide(UUID.randomUUID()));
            try {
                assertThat(policyClock.entered.await(10, TimeUnit.SECONDS)).isTrue();
                mutation.run();
            } finally { policyClock.release.countDown(); }
            assertThatThrownBy(() -> pending.get(20, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
        } finally { policyClock.entered = null; policyClock.release = null; }
        assertThat(decisionCount()).isZero();
    }

}
