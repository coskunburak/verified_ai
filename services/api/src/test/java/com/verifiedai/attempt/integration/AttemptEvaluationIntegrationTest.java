package com.verifiedai.attempt.integration;

import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.attempt.application.*;
import com.verifiedai.attempt.domain.*;
import com.verifiedai.attempt.infrastructure.persistence.*;
import com.verifiedai.attempt.infrastructure.privacy.StudentAttemptPrivacyLifecycleContributor;
import com.verifiedai.problem.application.attempt.*;
import com.verifiedai.problem.application.solving.*;
import com.verifiedai.problem.application.canonicalization.CanonicalProblemApplicationService;
import com.verifiedai.solving.SolverSourceFixture;
import com.verifiedai.solving.application.canonical.CanonicalMathCodec;
import com.verifiedai.verification.application.VerificationSourceReader;
import com.verifiedai.sharedkernel.error.*;
import com.verifiedai.sharedkernel.ratelimit.*;
import com.verifiedai.identity.application.AccountPrivacyApplicationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import static org.assertj.core.api.Assertions.*;

@Import(StudentAttemptIntegrationTest.StorageConfiguration.class)
class AttemptEvaluationIntegrationTest extends PostgresIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired CanonicalProblemApplicationService canonical;
    @Autowired SolveSourceAccess sources;
    @Autowired VerificationSourceReader reader;
    @Autowired ManualStepNormalizer parser;
    @Autowired AttemptExtractionReader extraction;
    @Autowired AttemptWorkNormalizer normalizer;
    @Autowired CanonicalMathCodec codec;
    @Autowired StudentAttemptRepository attempts;
    @Autowired AttemptWorkRepository works;
    @Autowired AttemptEvaluationRepository evaluations;
    @Autowired PlatformTransactionManager transactions;
    @Autowired AccountPrivacyApplicationService privacy;
    UUID owner,problem; AttemptSourceIdentity source;
    SimpleMeterRegistry metrics=new SimpleMeterRegistry();
    @BeforeEach void setup() {
        var fixture=new SolverSourceFixture(jdbc); owner=fixture.insertUser();
        problem=fixture.insertSupportedParse(owner,SolverSourceFixture.equationParseJson("2*x+3=11"),1);
        canonical.canonicalize(owner,problem);
        source=new TransactionTemplate(transactions).execute(t->{var s=sources.current(owner,problem);return new AttemptSourceIdentity(s.canonicalProblemId(),s.canonicalRevision(),s.problemParseId(),s.problemParseRevision());});
    }
    AttemptWorkService capture() {
        return new AttemptWorkService(sources,attempts,works,normalizer,parser,extraction,reader,codec,metrics,
            (p,k)->RateLimitDecision.allow(),Clock.systemUTC(),transactions,true,true,"test",false);
    }
    AttemptStructuredWork work(UUID prior) { return work(prior,"x=4"); }
    AttemptStructuredWork work(UUID prior,String text) {
        return capture().submit(owner,problem,UUID.randomUUID().toString(),new AttemptWorkInput("attempt-structured-work-request-v1",source,
            AttemptWorkInput.Engine.MANUAL,null,null,null,prior,List.of(),List.of(new AttemptWorkInput.Step(UUID.randomUUID(),text,AttemptWorkInput.Role.FINAL_ANSWER,List.of(),true))));
    }
    AttemptEvaluationService service(boolean enabled,AttemptVerifierClient verifier) {
        return new AttemptEvaluationService(sources,attempts,works,evaluations,new AttemptEvaluationPreparer(codec,reader),verifier,
            transactions,Clock.systemUTC(),(p,k)->RateLimitDecision.allow(),metrics,enabled,true,"test",false);
    }
    AttemptEvaluationInput input(AttemptStructuredWork work) { return new AttemptEvaluationInput("attempt-evaluation-request-v1",work.source(),work.revision()); }
    AttemptAnalysisResult unknown(UUID id,int count,String request) {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        var base=AttemptAnalysisResult.unavailable(id,count,AttemptAnalysisResult.Reason.UNKNOWN_DOMAIN);
        return new AttemptAnalysisResult("attempt-analysis-v2","bounded-attempt-v2","attempt-taxonomy-v2",id,id.toString(),base.outcome(),null,
            "UNSUPPORTED",base.steps(),null,codec.parse(request).path("domainBasis").asText(),null);
    }
    AttemptAnalysisResult proven(UUID id,int count,String request) {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        var steps=new ArrayList<AttemptAnalysisResult.Step>();
        for(int i=1;i<=count;i++) steps.add(new AttemptAnalysisResult.Step(i,StepVerdict.VALID_EQUIVALENT,
            AttemptAnalysisResult.Reason.SAME_SOLUTION_SET,null,AttemptAnalysisResult.WitnessStatus.NOT_APPLICABLE,null));
        return new AttemptAnalysisResult("attempt-analysis-v2","bounded-attempt-v2","attempt-taxonomy-v2",id,id.toString(),
            AttemptOutcome.INCOMPLETE_VALID_SO_FAR,null,"REAL_DOMAIN",steps,codec.parse("{\"kind\":\"REALS\"}"),
            codec.parse(request).path("domainBasis").asText(),codec.parse("{\"kind\":\"FINITE_SET\",\"elements\":[\"4\"]}"));
    }
    @Test void reviewedRealScopeAndFinalCompletenessRemainIndependent() {
        var work=work(null); var original=works.get(owner,work.attemptId());
        var command=new AttemptEvaluationInput("attempt-evaluation-request-v1",source,1,true);
        var result=service(true,this::proven).evaluate(owner,problem,work.attemptId(),"reviewed-domain-key",command);
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.evaluation().realDomainConfirmed()).isTrue();
        assertThat(result.evaluation().finalAnswerStatus()).isEqualTo(AttemptEvaluation.FinalStatus.VERIFIED);
        assertThat(result.evaluation().analysis().domainBasis()).isEqualTo("REVIEWED_REAL_TASK");
        assertThat(works.get(owner,work.attemptId())).isEqualTo(original);
        assertThatThrownBy(()->service(true,this::proven).evaluate(owner,problem,work.attemptId(),"reviewed-domain-key",input(work))).isInstanceOf(ApiProblemException.class);
        var incomplete=work(null,"2*x=8");
        var partial=service(true,this::proven).evaluate(owner,problem,incomplete.attemptId(),"incomplete-final-key",command);
        assertThat(partial.evaluation().analysis().outcome()).isEqualTo(AttemptOutcome.INCOMPLETE_VALID_SO_FAR);
        assertThat(partial.evaluation().finalAnswerStatus()).isEqualTo(AttemptEvaluation.FinalStatus.INCONCLUSIVE);
    }
    @Test void rateLimitAndForeignExecutionCannotPublish() {
        var work=work(null);
        var limited=new AttemptEvaluationService(sources,attempts,works,evaluations,new AttemptEvaluationPreparer(codec,reader),this::unknown,
            transactions,Clock.systemUTC(),(p,k)->RateLimitDecision.denied(60),metrics,true,true,"test",false);
        assertThatThrownBy(()->limited.evaluate(owner,problem,work.attemptId(),"rate-limit-key",input(work)))
            .isInstanceOfSatisfying(ApiProblemException.class,e->assertThat(e.code()).isEqualTo(ApiErrorCode.RATE_LIMIT_EXCEEDED));
        var response=service(true,(id,n,r)->unknown(UUID.randomUUID(),n,r)).evaluate(owner,problem,work.attemptId(),"foreign-result-key",input(work));
        assertThat(response.status()).isEqualTo("RETRYABLE"); assertThat(evaluations.export(owner)).isEmpty();
    }
    @Test void durableIndependentEvidencePrivacyRollbackAndImmutability() {
        var work=work(null); var before=works.get(owner,work.attemptId()).orElseThrow();
        var value=service(true,this::unknown).evaluate(owner,problem,work.attemptId(),"evaluation-key",input(work));
        assertThat(value.status()).isEqualTo("COMPLETED");
        assertThat(value.evaluation().finalAnswerStatus()).isEqualTo(AttemptEvaluation.FinalStatus.INCONCLUSIVE);
        assertThat(value.evaluation().finalAnswerEvidence()).isNotNull();
        assertThat(value.evaluation().stepIds()).containsExactly(work.steps().getFirst().id());
        assertThat(works.get(owner,work.attemptId())).contains(before);
        assertThat(attempts.get(owner,problem,work.attemptId()).orElseThrow().analysis()).isNull();
        assertThat(service(false,(a,b,c)->{throw new AssertionError();}).read(owner,problem,work.attemptId()).evaluation()).isEqualTo(value.evaluation());
        assertThat(service(false,(a,b,c)->{throw new AssertionError();}).evaluate(owner,problem,work.attemptId(),"evaluation-key",input(work)).evaluation()).isEqualTo(value.evaluation());
        for(String sql:List.of("update attempt_evaluations set artifact='{}'::jsonb where attempt_id=?", "update attempt_evaluations set training_eligible=true where attempt_id=?", "delete from attempt_evaluations where attempt_id=?"))
            assertThatThrownBy(()->jdbc.update(sql,work.attemptId())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(new StudentAttemptPrivacyLifecycleContributor(attempts).exportUserData(owner).toString()).contains("evaluations","INCONCLUSIVE");
        assertThat(jdbc.queryForObject("select count(*) from learner_mistake_events where user_id=?",Integer.class,owner)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from learner_skill_mastery where user_id=?",Integer.class,owner)).isZero();
        privacy.requestDeletion(owner); privacy.confirmDeletion(owner,"DELETE");
        assertThat(evaluations.export(owner)).isEmpty();
    }
    @Test void concurrencyReplayAndDifferentPayloadConflictNeverDuplicateArtifact() throws Exception {
        var work=work(null); var entered=new CountDownLatch(1); var release=new CountDownLatch(1); var calls=new AtomicInteger();
        var service=service(true,(id,count,request)->{
            calls.incrementAndGet(); entered.countDown();
            try { if(!release.await(5,TimeUnit.SECONDS)) throw new IllegalStateException(); } catch(InterruptedException ex) { throw new IllegalStateException(ex); }
            return unknown(id,count,request);
        });
        var pool=Executors.newSingleThreadExecutor();
        try {
            var first=pool.submit(()->service.evaluate(owner,problem,work.attemptId(),"concurrent-key",input(work)));
            assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            var replay=service.evaluate(owner,problem,work.attemptId(),"concurrent-key",input(work));
            assertThat(replay.status()).isEqualTo("IN_PROGRESS"); assertThat(calls.get()).isEqualTo(1);
            assertThatThrownBy(()->service.evaluate(owner,problem,work.attemptId(),"different-key",input(work))).isInstanceOfSatisfying(ApiProblemException.class,e->assertThat(e.code()).isEqualTo(ApiErrorCode.IDEMPOTENCY_KEY_REUSED));
            release.countDown(); assertThat(first.get(10,TimeUnit.SECONDS).status()).isEqualTo("COMPLETED");
            assertThat(evaluations.export(owner)).hasSize(1);
            assertThatThrownBy(()->service.evaluate(owner,problem,work.attemptId(),"concurrent-key",new AttemptEvaluationInput("attempt-evaluation-request-v1",source,2))).isInstanceOf(ApiProblemException.class);
            var second=work(null);
            assertThatThrownBy(()->service.evaluate(owner,problem,second.attemptId(),"concurrent-key",input(second))).isInstanceOf(ApiProblemException.class);
        } finally { release.countDown(); pool.shutdownNow(); }
    }
    @Test void failuresAreRecoverableAndLateExecutionCannotOverwriteNewLease() {
        var work=work(null);
        for(var reason:List.of(AttemptAnalysisResult.Reason.TIMEOUT,AttemptAnalysisResult.Reason.ADMISSION_LIMIT,AttemptAnalysisResult.Reason.INVALID_RESPONSE,AttemptAnalysisResult.Reason.WORKER_FAILURE)) {
            var response=service(true,(id,n,r)->AttemptAnalysisResult.unavailable(id,n,reason)).evaluate(owner,problem,work.attemptId(),"retryable-key",input(work));
            assertThat(response.status()).isEqualTo("RETRYABLE"); assertThat(response.evaluation()).isNull();
        }
        var old=evaluations.get(owner,work.attemptId()).orElseThrow();
        var result=service(true,this::unknown).evaluate(owner,problem,work.attemptId(),"retryable-key",input(work));
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.evaluation().executionId()).isNotEqualTo(old.executionId());
        evaluations.release(owner,old,Instant.now());
        assertThat(evaluations.get(owner,work.attemptId()).orElseThrow().artifact()).isEqualTo(result.evaluation());
    }
    @Test void sourceChangeSupersessionAndDeletedOwnerBlockLatePublication() {
        var work=work(null);
        var value=service(true,(id,n,r)->{
            jdbc.update("update problem_sessions set current_parse_id=null where id=?",problem); return unknown(id,n,r);
        }).evaluate(owner,problem,work.attemptId(),"source-race-key",input(work));
        assertThat(value.status()).isEqualTo("STALE"); assertThat(evaluations.export(owner)).isEmpty();
        jdbc.update("update problem_sessions set current_parse_id=? where id=?",source.parseId(),problem);
        var revised=work(work.attemptId());
        assertThatThrownBy(()->service(true,this::unknown).evaluate(owner,problem,work.attemptId(),"source-race-key",input(work))).isInstanceOfSatisfying(ApiProblemException.class,e->assertThat(e.code()).isEqualTo(ApiErrorCode.ATTEMPT_STALE_SOURCE));
        assertThatThrownBy(()->service(true,this::unknown).read(new SolverSourceFixture(jdbc).insertUser(),problem,revised.attemptId())).isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(()->service(true,(id,n,r)->{
            privacy.requestDeletion(owner); privacy.confirmDeletion(owner,"DELETE"); return unknown(id,n,r);
        }).evaluate(owner,problem,revised.attemptId(),"deletion-race-key",input(revised))).isInstanceOf(ApiProblemException.class);
        assertThat(evaluations.export(owner)).isEmpty();
    }
    @Test void sessionDeletionUsesParentCascadeAndRemovesPendingAndPublishedEvidence() {
        var saved=work(null);
        service(true,this::unknown).evaluate(owner,problem,saved.attemptId(),"cascade-published",input(saved));
        var pending=work(null);
        service(true,(id,n,r)->{throw new IllegalStateException("synthetic outage");})
            .evaluate(owner,problem,pending.attemptId(),"cascade-pending",input(pending));
        new TransactionTemplate(transactions).executeWithoutResult(t->{
            sources.lockOwner(owner,problem);
            jdbc.update("delete from problem_sessions where id=? and user_id=?",problem,owner);
        });
        assertThat(evaluations.export(owner)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from attempt_evaluations where user_id=?",Integer.class,owner)).isZero();
    }
    @Test void unexpiredOrAlreadyReclaimedLeaseCannotBeStolen() {
        var work=work(null);
        var tx=new TransactionTemplate(transactions);
        var now=Instant.now();
        var first=tx.execute(t->{sources.lockOwner(owner,problem);return evaluations.create(owner,problem,"lease-fence-key",work,false,now);});
        assertThatThrownBy(()->tx.execute(t->{sources.lockOwner(owner,problem);return evaluations.reclaim(owner,first,now);}))
            .isInstanceOfSatisfying(ApiProblemException.class,e->assertThat(e.code()).isEqualTo(ApiErrorCode.OPTIMISTIC_CONFLICT));
        var next=tx.execute(t->{sources.lockOwner(owner,problem);return evaluations.reclaim(owner,first,now.plusSeconds(21));});
        assertThat(next.executionId()).isNotEqualTo(first.executionId());
        assertThatThrownBy(()->tx.execute(t->{sources.lockOwner(owner,problem);return evaluations.reclaim(owner,first,now.plusSeconds(50));}))
            .isInstanceOf(ApiProblemException.class);
    }
    @Test void upgradeFromV034DoesNotBackfillAssessments() throws Exception {
        String schema="evaluation_upgrade_"+UUID.randomUUID().toString().replace("-","");
        try {
            org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).locations("classpath:db/migration/platform").target("34").load().migrate();
            try(var connection=dataSource.getConnection()) {
                String previousSchema=connection.getSchema();
                try {
                    connection.setSchema(schema);
                    var isolated=new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true));
                    assertThat(new AttemptEvaluationRepository(isolated).available()).isFalse();
                    org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).locations("classpath:db/migration/platform").load().migrate();
                    assertThat(new AttemptEvaluationRepository(isolated).available()).isTrue();
                    assertThat(isolated.queryForObject("select count(*) from attempt_evaluations",Integer.class)).isZero();
                } finally {
                    // Restore before returning a pooled connection or dropping the temporary schema.
                    // Otherwise later tests inherit a search_path pointing at a deleted schema.
                    connection.setSchema(previousSchema);
                }
                assertThat(connection.getSchema()).isEqualTo(previousSchema);
                assertThat(new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true))
                    .queryForObject("select to_regclass('users') is not null",Boolean.class)).isTrue();
            }
        } finally { jdbc.execute("drop schema if exists "+schema+" cascade"); }
    }
}
