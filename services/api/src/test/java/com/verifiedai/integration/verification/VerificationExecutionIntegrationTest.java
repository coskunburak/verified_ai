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

@TestPropertySource(properties = {"app.verification.execution-enabled=true", "app.verification.owner-accepted=true",
    "app.verification.methods=DOMAIN_CONSTRAINT_CHECK,EQUATION_SUBSTITUTION,SOLUTION_SET_COMPLETENESS"})
@Import(VerificationExecutionIntegrationTest.Configuration.class)
class VerificationExecutionIntegrationTest extends PostgresIntegrationTestSupport {
    @TestConfiguration static class Configuration {
        @Bean @Primary ControlledExecutor controlledExecutor() { return new ControlledExecutor(); }
        // Ingestion fixtures contain synthetic object metadata; exercise real account/DB deletion without external S3.
        @Bean @Primary ProblemAssetStorage syntheticAssetStorage() {
            return org.mockito.Mockito.mock(ProblemAssetStorage.class);
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
    @Test void durableEvidenceRetriesAndExportPreserveUnverifiedSolution() {
        var first = run();
        assertThat(first.state()).isEqualTo(VerificationExecution.State.PUBLISHED);
        assertThat(first.run().evidence()).hasSize(3).allSatisfy(e -> {
            assertThat(e.result().outcome()).isEqualTo(Outcome.EXECUTION_FAILED); assertThat(e.result().proof()).isNull();
        });
        assertThat(run().state()).isEqualTo(VerificationExecution.State.REUSED); assertThat(count()).isEqualTo(1);
        assertThat(privacy.exportUserData(user).get("runs")).asList().hasSize(1);
        var export = accountPrivacy.requestExport(user);
        assertThat(export.status()).isEqualTo("READY");
        com.fasterxml.jackson.databind.JsonNode exported = new com.fasterxml.jackson.databind.ObjectMapper()
            .valueToTree(accountPrivacy.downloadExport(user, export.exportId()));
        assertThat(exported.path("verificationEvidence").path("runs").size()).isEqualTo(1);
        var exportedRun = exported.path("verificationEvidence").path("runs").get(0);
        assertThat(exportedRun.path("id").asText()).isEqualTo(first.run().id().toString());
        assertThat(exportedRun.path("createdAt").asText()).isEqualTo(first.run().createdAt().toString());
        assertThat(exportedRun.path("evidence").size()).isEqualTo(first.run().evidence().size());
        for (int index = 0; index < first.run().evidence().size(); index++) {
            var expected = first.run().evidence().get(index);
            var actual = exportedRun.path("evidence").get(index);
            assertThat(actual.path("id").asText()).isEqualTo(expected.id().toString());
            assertThat(actual.path("createdAt").asText()).isEqualTo(expected.createdAt().toString());
            assertThat(actual.path("result").path("outcome").asText()).isEqualTo("EXECUTION_FAILED");
            assertThat(actual.path("result").path("proof").isNull()).isTrue();
        }
        assertThat(solutions.historical(user, session, solutionId).state()).isEqualTo(CanonicalSolutions.ReadState.GENERATED_UNVERIFIED);
        assertThat(execution.history(user, session, solutionId, key).run()).isEqualTo(first.run());
    }
    @Test void crossOwnerAccessAndCallerTransactionsAreRejected() {
        var other = fixture.insertUser();
        assertThatThrownBy(() -> execution.execute(other, session, solutionId, key)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> execution.history(other, session, solutionId, key)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> tx.execute(s -> run())).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(count()).isZero();
    }
    @Test void historyIsImmutableAndExplicitAfterSelectedSourceChanges() {
        var first = run().run();
        fixture.insertSupportedParse(user, session, SolverSourceFixture.equationParseJson("x + 2 = 4"), 2);
        canonical.canonicalize(user, session);
        var history = execution.history(user, session, solutionId, key);
        assertThat(history.currentSource()).isFalse(); assertThat(history.run()).isEqualTo(first);
        assertThat(run().run()).isNull(); assertThat(count()).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("update verification_runs set policy_version='tampered' where id=?", first.id()))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from verification_runs where id=?", first.id()))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void ownedSourceAndAccountDeletionCascadeAllVerificationEvidence() {
        run(); jdbc.update("delete from problem_sessions where id=?", session); assertThat(count()).isZero();
        setup(); run();
        var objectKeys = jdbc.queryForList("""
            select object_key from problem_assets where user_id=? and status<>'DELETED'
            union all
            select object_key from problem_asset_derivatives where user_id=? and object_key is not null
            """, String.class, user, user);
        assertThat(objectKeys).isNotEmpty();
        assertThat(accountPrivacy.requestDeletion(user).status()).isEqualTo("DELETION_REQUESTED");
        assertThat(accountPrivacy.confirmDeletion(user, "DELETE").status()).isEqualTo("DELETED");
        assertThat(count()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from problem_sessions where user_id=?", Integer.class, user)).isZero();
        assertThat(jdbc.queryForObject("select status from users where id=?", String.class, user)).isEqualTo("DELETED");
        for (var objectKey : objectKeys) org.mockito.Mockito.verify(assetStorage).deleteIfExists(objectKey);
    }
    @Test void idempotencyKeyCannotBeReboundToAnotherSolution() {
        run(); var another = materialize();
        assertThat(another).isNotEqualTo(solutionId);
        assertThat(solutions.historical(user, session, another).state()).isEqualTo(CanonicalSolutions.ReadState.GENERATED_UNVERIFIED);
        assertThat(execution.execute(user, session, another, key).state()).isEqualTo(VerificationExecution.State.BLOCKED);
        assertThat(count()).isEqualTo(1);
        assertThat(execution.history(user, session, solutionId, key).run().solutionId()).isEqualTo(solutionId);
    }
    @Test void twoConcurrentExecutionsReachGatewayWithoutOwnerLockAndPublishOnce() throws Exception {
        controlled.entered = new CountDownLatch(2); controlled.release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(this::run); var second = pool.submit(this::run);
            try { assertThat(controlled.entered.await(10, TimeUnit.SECONDS)).isTrue(); }
            finally { controlled.release.countDown(); }
            assertThat(List.of(first.get(20, TimeUnit.SECONDS).state(), second.get(20, TimeUnit.SECONDS).state()))
                .containsExactlyInAnyOrder(VerificationExecution.State.PUBLISHED, VerificationExecution.State.REUSED);
            assertThat(count()).isEqualTo(1);
        }
    }
    @Test void deletionDuringNetworkWorkCannotPublish() throws Exception {
        controlled.entered = new CountDownLatch(1); controlled.release = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var pending = pool.submit(this::run);
            try {
                assertThat(controlled.entered.await(10, TimeUnit.SECONDS)).isTrue();
                jdbc.update("update users set status='DELETION_REQUESTED' where id=?", user);
            } finally { controlled.release.countDown(); }
            assertThatThrownBy(() -> pending.get(20, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
            assertThat(count()).isZero();
        }
    }
    @Test void transactionRollbackLeavesNoPartiallyPublishedRun() {
        run();
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            jdbc.update("delete from problem_sessions where id=?", session);
            throw new IllegalStateException("owner rollback fixture");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(count()).isEqualTo(1);
    }
    @Test void compositeForeignKeyRejectsForgedOwnerEvenWithConsistentJson() {
        var original = run().run(); var other = fixture.insertUser(); var id = UUID.randomUUID();
        var source = original.source();
        var forgedSource = new PlanningSource.Identity(other, source.sessionId(), source.canonicalProblemId(), source.parseId(),
            source.parseRevision(), source.canonicalRevision());
        var evidence = original.evidence().stream().map(e -> new VerificationEvidence(
            VerificationEvidence.identity(id, e.result().method()), id, e.planId(), e.policyVersion(), e.capabilityVersion(),
            forgedSource, e.solutionId(), e.solutionVersion(), e.claim(), e.domainAssumptions(), e.result(), e.correlationId(), e.createdAt())).toList();
        var forged = new VerificationRun(id, UUID.randomUUID(), original.planId(), original.policyVersion(), original.capabilityVersion(),
            forgedSource, original.solutionId(), original.solutionVersion(), original.correlationId(), original.createdAt(), evidence);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> runs.publish(forged)))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(count()).isEqualTo(1);
    }
    @Test void sourceRevisionDuringNetworkWorkReturnsStaleWithoutPublication() throws Exception {
        controlled.entered = new CountDownLatch(1); controlled.release = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var pending = pool.submit(this::run);
            try {
                assertThat(controlled.entered.await(10, TimeUnit.SECONDS)).isTrue();
                fixture.insertSupportedParse(user, session, SolverSourceFixture.equationParseJson("x + 2 = 4"), 2);
                canonical.canonicalize(user, session);
            } finally { controlled.release.countDown(); }
            assertThat(pending.get(20, TimeUnit.SECONDS).state()).isEqualTo(VerificationExecution.State.STALE_SOURCE);
            assertThat(count()).isZero();
        }
    }
}
