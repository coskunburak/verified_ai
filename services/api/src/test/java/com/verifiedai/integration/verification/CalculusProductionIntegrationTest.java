package com.verifiedai.integration.verification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verifiedai.calculus.*;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.problem.application.canonicalization.CanonicalProblemApplicationService;
import com.verifiedai.problem.application.parse.*;
import com.verifiedai.problem.application.solving.*;
import com.verifiedai.problem.domain.port.ProblemAssetStorage;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.solving.SolverSourceFixture;
import com.verifiedai.solving.application.*;
import com.verifiedai.solving.application.canonical.*;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution.*;
import com.verifiedai.verification.application.*;
import com.verifiedai.verification.domain.model.*;
import com.verifiedai.verification.domain.policy.VerificationCapabilities;
import com.verifiedai.verification.infrastructure.http.HttpMathematicalExecutor;
import com.verifiedai.verification.infrastructure.privacy.VerificationLifecycleContributor;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.support.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="VERIFIEDAI_MATH_WIRE",matches="true")
@TestPropertySource(properties={"app.environment=test","app.verification.calculus-enabled=true",
    "app.verification.calculus-owner-accepted=true","app.solving.calculus-fixture-enabled=true"})
@Import(CalculusProductionIntegrationTest.Configuration.class)
class CalculusProductionIntegrationTest extends PostgresIntegrationTestSupport {
    static final CalculusHttpHarness HTTP=new CalculusHttpHarness();
    @DynamicPropertySource static void verifier(DynamicPropertyRegistry registry) { registry.add("app.verification.base-url",HTTP::url); }
    @AfterAll static void stopVerifier() { HTTP.close(); }
    @TestConfiguration static class Configuration {
        @Bean @Primary ControlledCalculus calculusControl(HttpMathematicalExecutor http) { return new ControlledCalculus(http); }
        @Bean @Primary ProblemAssetStorage syntheticStorage() { return org.mockito.Mockito.mock(ProblemAssetStorage.class); }
    }
    static class ControlledCalculus implements CalculusExecutor {
        final HttpMathematicalExecutor http; final AtomicInteger calls=new AtomicInteger();
        volatile CountDownLatch entered,release;
        volatile boolean malformed;
        ControlledCalculus(HttpMathematicalExecutor http) { this.http=http; }
        public boolean calculusReady() {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); return http.calculusReady();
        }
        public CalculusExecution.Result execute(CalculusExecution.Request request) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); calls.incrementAndGet();
            var result=http.execute(request);
            if (malformed) return new CalculusExecution.Result(UUID.randomUUID(),result.correlationId(),result.scope(),result.method(),
                result.methodVersion(),result.verifierVersion(),result.outcome(),result.reasonCode(),result.restrictionIds(),result.proof(),result.diagnostic());
            var signal=entered; var wait=release;
            if (signal!=null) {
                signal.countDown();
                try { if (!wait.await(15,TimeUnit.SECONDS)) throw new IllegalStateException("Release timed out"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }
            return result;
        }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired ProblemParseCorrectionApplicationService corrections;
    @Autowired CanonicalProblemApplicationService canonical;
    @Autowired PrimarySolveService solving;
    @Autowired CanonicalSolutions solutions;
    @Autowired SolveSourceAccess sources;
    @Autowired VerificationExecution execution;
    @Autowired VerificationPlanning planning;
    @Autowired VerificationLifecycleContributor privacy;
    @Autowired VerificationCapabilities capabilities;
    @Autowired ControlledCalculus controlled;
    @Autowired com.verifiedai.identity.application.AccountPrivacyApplicationService accountPrivacy;
    SolverSourceFixture fixture;
    UUID user,session;
    @BeforeEach void setup() {
        fixture=new SolverSourceFixture(jdbc); user=fixture.insertUser(); controlled.entered=null; controlled.release=null;
        controlled.calls.set(0); controlled.malformed=false; capabilities.configureCalculus(true,true,"bounded-calculus-v1","test");
    }
    UUID sourceAndCandidate(JsonNode example) {
        session=fixture.insertSupportedParse(user,SolverSourceFixture.equationParseJson("x + 1 = 2"),1);
        correction(example.path("parse")); canonical.canonicalize(user,session);
        var source=tx.execute(status->sources.current(user,session));
        var candidate=CalculusIntegrationData.candidate(source,example.path("candidateExpression"));
        var command=new SolveCommand(user,session,"calculus-fixture-"+UUID.randomUUID(),null);
        var job=solving.acceptCalculusFixture(command,candidate.toString());
        assertThat(solving.acceptCalculusFixture(command,candidate.toString()).jobId()).isEqualTo(job.jobId());
        var materialized=solutions.materialize(user,session,new Origin(Role.PRIMARY,job.jobId()));
        assertThat(materialized.state()).isEqualTo(CanonicalSolutions.MaterializationState.MATERIALIZED);
        return materialized.solutionId();
    }
    void correction(JsonNode parse) {
        var review=corrections.getParseReview(user,session);
        var base=review.currentParse();
        var result=corrections.createCalculusCorrection(new ProblemParseCorrectionCommand(user,session,base.problemParseId(),
            base.revision(),"calculus-correction-"+UUID.randomUUID(),"OTHER",parse.toString()));
        assertThat(result.selected()).isTrue(); assertThat(result.reviewRequired()).isFalse();
    }
    @Test void acceptedTypedSourcesReachRealWorkerAndPublishExactOrNonproofImmutableEvidence() {
        for (var example : CalculusIntegrationData.cases()) {
            UUID solution=sourceAndCandidate(example); UUID key=UUID.randomUUID();
            assertThat(planning.plan(user,session,solution).plan().executionAvailability())
                .isEqualTo(VerificationPlan.ExecutionAvailability.EXECUTABLE);
            var publication=execution.execute(user,session,solution,key);
            assertThat(publication.state()).isEqualTo(VerificationExecution.State.PUBLISHED);
            var evidence=publication.run().evidence().getFirst();
            assertThat(evidence.result()).isNull(); var result=evidence.calculus().result();
            assertThat(result.outcome().name()).as(example.path("id").asText()).isEqualTo(example.path("expectedOutcome").asText());
            assertThat(result.reasonCode().name()).isEqualTo(example.path("expectedReason").asText());
            if (example.path("expectedOutcome").asText().equals("INCONCLUSIVE")) {
                assertThat(result.proof()).isNull(); assertThat(result.diagnostic()).isNotNull();
                assertThat(result.diagnostic().evaluated()).isPositive();
                assertThat(result.diagnostic().agreeing()).isEqualTo(result.diagnostic().evaluated());
            }
            var historical=execution.history(user,session,solution,key);
            assertThat(historical.currentSource()).isTrue(); assertThat(historical.run()).isEqualTo(publication.run());
            assertThat(execution.execute(user,session,solution,key).state()).isEqualTo(VerificationExecution.State.REUSED);
            assertThat(solutions.historical(user,session,solution).state()).isEqualTo(CanonicalSolutions.ReadState.GENERATED_UNVERIFIED);
            assertThat(privacy.exportUserData(user).toString()).contains("calculus-evidence-v1");
            assertThatThrownBy(()->tx.execute(status->jdbc.update("update verification_runs set run_jsonb=run_jsonb where id=?",publication.run().id())))
                .isInstanceOf(RuntimeException.class);
        }
    }
    @Test void correctionDuringRealHttpMakesPublicationStaleAndCannotPublishForeignOrHistoricalCandidate() throws Exception {
        UUID solution=sourceAndCandidate(CalculusIntegrationData.cases().getFirst()); var key=UUID.randomUUID();
        controlled.entered=new CountDownLatch(1); controlled.release=new CountDownLatch(1);
        var worker=Executors.newSingleThreadExecutor();
        try {
            UUID currentSession=session;
            var pending=worker.submit(()->execution.execute(user,currentSession,solution,key));
            assertThat(controlled.entered.await(15,TimeUnit.SECONDS)).isTrue();
            var revised=(ObjectNode)CalculusIntegrationData.cases().getFirst().path("parse").deepCopy();
            ((ObjectNode)revised.path("calculus").path("original").path("right")).put("value","4");
            var display=(ObjectNode)revised.path("expressions").get(0);
            display.put("normalizedText","x^4").put("sourceText","x^4").put("displayLatex","x^4");
            correction(revised); canonical.canonicalize(user,session); controlled.release.countDown();
            assertThat(pending.get(15,TimeUnit.SECONDS).state()).isEqualTo(VerificationExecution.State.STALE_SOURCE);
            assertThat(jdbc.queryForObject("select count(*) from verification_runs where user_id=?",Integer.class,user)).isZero();
            assertThat(planning.plan(user,session,solution).state()).isEqualTo(VerificationPlan.State.STALE_SOURCE);
        } finally { controlled.release.countDown(); worker.shutdownNow(); }
        UUID other=fixture.insertUser(); int before=controlled.calls.get();
        assertThatThrownBy(()->execution.execute(other,session,solution,key)).isInstanceOf(ApiProblemException.class);
        assertThat(controlled.calls.get()).isEqualTo(before);
    }
    @Test void sourceRestrictionsRetainTheirTypedAstThroughPublicationRetrievalAndPrivacyExport() throws Exception {
        var example=(ObjectNode)CalculusIntegrationData.cases().getFirst().deepCopy();
        var calculus=(ObjectNode)example.path("parse").path("calculus");
        var zero=CalculusIntegrationData.JSON.createObjectNode().put("numerator","0").put("denominator","1");
        var two=CalculusIntegrationData.JSON.createObjectNode().put("numerator","2").put("denominator","1");
        ((ObjectNode)calculus.path("interval")).set("lower",zero);
        ((ObjectNode)calculus.path("interval")).set("upper",two);
        var restriction=calculus.withArray("restrictions").addObject().put("id","positive-x").put("relation","GREATER_THAN");
        restriction.putObject("left").put("kind","VARIABLE").put("symbol","x");
        restriction.putObject("right").put("kind","NUMBER").put("numericType","INTEGER").put("value","0");
        UUID solution=sourceAndCandidate(example),key=UUID.randomUUID();
        var publication=execution.execute(user,session,solution,key);
        var evidence=publication.run().evidence().getFirst().calculus();
        assertThat(evidence.result().outcome()).isEqualTo(MathematicalExecution.Outcome.PROVEN);
        assertThat(evidence.restrictions()).hasSize(1);
        assertThat(execution.history(user,session,solution,key).run()).isEqualTo(publication.run());
        var exported=CalculusIntegrationData.JSON.readTree(CalculusIntegrationData.JSON.writeValueAsString(privacy.exportUserData(user)));
        var stored=exported.path("runs").get(0).path("evidence").get(0).path("calculus");
        assertThat(stored.path("restrictions").get(0).path("left").path("kind").asText()).isEqualTo("VARIABLE");
        assertThat(stored.path("result").path("scope").path("identity").asText()).isEqualTo(evidence.result().scope().identity().toString());
    }
    @Test void admissionRejectsForeignEvidenceStaleRevisionAndAmbiguousSourceWithoutPublishing() {
        session=fixture.insertSupportedParse(user,SolverSourceFixture.equationParseJson("x + 1 = 2"),1);
        var base=corrections.getParseReview(user,session).currentParse();
        var parse=(ObjectNode)CalculusIntegrationData.cases().getFirst().path("parse").deepCopy();
        ((ObjectNode)parse.path("sourceEvidenceRefs").get(0)).put("blockId","foreign-block");
        assertThatThrownBy(()->corrections.createCalculusCorrection(new ProblemParseCorrectionCommand(user,session,base.problemParseId(),
            base.revision(),"foreign-evidence-key","OTHER",parse.toString()))).isInstanceOf(ApiProblemException.class);
        var valid=CalculusIntegrationData.cases().getFirst().path("parse");
        var accepted=corrections.createCalculusCorrection(new ProblemParseCorrectionCommand(user,session,base.problemParseId(),
            base.revision(),"accepted-calculus-key","OTHER",valid.toString()));
        assertThat(accepted.selected()).isTrue();
        canonical.canonicalize(user,session);
        assertThatThrownBy(()->corrections.createCalculusCorrection(new ProblemParseCorrectionCommand(user,session,base.problemParseId(),
            base.revision(),"outdated-calculus-key","OTHER",valid.toString()))).isInstanceOf(ApiProblemException.class);
        var uncertain=(ObjectNode)valid.deepCopy(); uncertain.put("reviewRequired",true);
        ((ObjectNode)uncertain.path("uncertainty")).withArray("parse").add("Ambiguous one-sided approach");
        ((ObjectNode)uncertain.path("calculus").path("interval")).set("lower",
            CalculusIntegrationData.JSON.createObjectNode().put("numerator","0").put("denominator","1"));
        var reviewed=corrections.createCalculusCorrection(new ProblemParseCorrectionCommand(user,session,accepted.problemParseId(),
            accepted.revision(),"review-calculus-key","OTHER",uncertain.toString()));
        assertThat(reviewed.reviewRequired()).isTrue();
        assertThat(reviewed.selected()).isTrue();
        assertThat(jdbc.queryForObject("select status from problem_sessions where id=?",String.class,session))
            .isEqualTo("REVIEW_REQUIRED");
        assertThatThrownBy(()->canonical.canonicalize(user,session)).isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(()->tx.execute(status->sources.current(user,session))).isInstanceOf(ApiProblemException.class);
        assertThat(jdbc.queryForObject("select count(*) from verification_runs where user_id=?",Integer.class,user)).isZero();
        assertThat(controlled.calls.get()).isZero();
        var recovered=corrections.createCalculusCorrection(new ProblemParseCorrectionCommand(user,session,reviewed.problemParseId(),
            reviewed.revision(),"resolved-calculus-key","OTHER",valid.toString()));
        assertThat(recovered.selected()).isTrue();
        assertThat(recovered.reviewRequired()).isFalse();
        assertThat(jdbc.queryForObject("select status from problem_sessions where id=?",String.class,session)).isEqualTo("PARSED");
        canonical.canonicalize(user,session);
        assertThat(tx.execute(status->sources.current(user,session)).problemParseId()).isEqualTo(recovered.problemParseId());
    }
    @Test void malformedVerifierResultBecomesTypedNonproofAndAccountDeletionRemovesItsEvidence() {
        UUID solution=sourceAndCandidate(CalculusIntegrationData.cases().getFirst()); var key=UUID.randomUUID();
        controlled.malformed=true;
        var publication=execution.execute(user,session,solution,key);
        assertThat(publication.state()).isEqualTo(VerificationExecution.State.PUBLISHED);
        var result=publication.run().evidence().getFirst().calculus().result();
        assertThat(result.outcome()).isEqualTo(MathematicalExecution.Outcome.EXECUTION_FAILED);
        assertThat(result.reasonCode()).isEqualTo(CalculusExecution.Reason.INVALID_RESPONSE);
        assertThat(result.proof()).isNull();
        assertThat(accountPrivacy.requestDeletion(user).status()).isEqualTo("DELETION_REQUESTED");
        assertThat(accountPrivacy.confirmDeletion(user,"DELETE").status()).isEqualTo("DELETED");
        assertThat(jdbc.queryForObject("select count(*) from verification_runs where user_id=?",Integer.class,user)).isZero();
        assertThatThrownBy(()->execution.history(user,session,solution,key)).isInstanceOf(ApiProblemException.class);
    }
    @Test void simultaneousRetriesPublishOneRunAndRollbackRetainsHistoryWithoutCallingMath() throws Exception {
        UUID solution=sourceAndCandidate(CalculusIntegrationData.cases().getFirst()); var key=UUID.randomUUID();
        var workers=Executors.newFixedThreadPool(2);
        try {
            var one=workers.submit(()->execution.execute(user,session,solution,key));
            var two=workers.submit(()->execution.execute(user,session,solution,key));
            var states=List.of(one.get(20,TimeUnit.SECONDS).state(),two.get(20,TimeUnit.SECONDS).state());
            assertThat(states).containsExactlyInAnyOrder(VerificationExecution.State.PUBLISHED,VerificationExecution.State.REUSED);
        } finally { workers.shutdownNow(); }
        assertThat(jdbc.queryForObject("select count(*) from verification_runs where user_id=?",Integer.class,user)).isEqualTo(1);
        capabilities.configureCalculus(false,true,"bounded-calculus-v1","test"); int before=controlled.calls.get();
        assertThat(execution.execute(user,session,solution,UUID.randomUUID()).state()).isEqualTo(VerificationExecution.State.BLOCKED);
        assertThat(execution.history(user,session,solution,key).run().evidence().getFirst().calculus()).isNotNull();
        assertThat(controlled.calls.get()).isEqualTo(before);
        tx.executeWithoutResult(status->jdbc.update("delete from problem_sessions where id=? and user_id=?",session,user));
        assertThat(jdbc.queryForObject("select count(*) from verification_runs where user_id=?",Integer.class,user)).isZero();
    }
}
