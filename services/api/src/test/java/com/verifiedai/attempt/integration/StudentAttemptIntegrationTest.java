package com.verifiedai.attempt.integration;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.attempt.application.*;
import com.verifiedai.attempt.domain.*;
import com.verifiedai.attempt.infrastructure.persistence.StudentAttemptRepository;
import com.verifiedai.attempt.infrastructure.privacy.StudentAttemptPrivacyLifecycleContributor;
import com.verifiedai.problem.application.solving.SolveSourceAccess;
import com.verifiedai.problem.application.attempt.ManualStepNormalizer;
import com.verifiedai.problem.application.canonicalization.CanonicalProblemApplicationService;
import com.verifiedai.solving.application.canonical.CanonicalMathCodec;
import com.verifiedai.verification.application.VerificationSourceReader;
import com.verifiedai.solving.SolverSourceFixture;
import com.verifiedai.sharedkernel.error.*;
import com.verifiedai.sharedkernel.ratelimit.RateLimitDecision;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

class StudentAttemptIntegrationTest extends PostgresIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired CanonicalProblemApplicationService canonical;
    @Autowired SolveSourceAccess sources;
    @Autowired VerificationSourceReader reader;
    @Autowired ManualStepNormalizer normalizer;
    @Autowired CanonicalMathCodec codec;
    @Autowired StudentAttemptRepository store;
    @Autowired PlatformTransactionManager transactions;
    UUID owner,problem; AttemptSourceIdentity source;
    @BeforeEach void setup() {
        // Active forward migrations must install the store; planned SQL is never a test fallback.
        assertThat(store.available()).isTrue();
        var fixture=new SolverSourceFixture(jdbc);owner=fixture.insertUser();
        problem=fixture.insertSupportedParse(owner,SolverSourceFixture.equationParseJson("2*x + 3 = 11"),1);
        canonical.canonicalize(owner,problem);
        source=service(review(),true).context(owner,problem).source();
    }
    AttemptVerifierClient review() { return (id,count,request)->AttemptAnalysisResult.unavailable(id,count,AttemptAnalysisResult.Reason.UNSUPPORTED_MATH); }
    StudentAttemptOrchestrator service(AttemptVerifierClient verifier,boolean enabled) {
        return new StudentAttemptOrchestrator(sources,reader,normalizer,codec,store,verifier,transactions,Clock.systemUTC(),new SimpleMeterRegistry(),
            (p,k)->RateLimitDecision.allow(),enabled,"test");
    }
    @Test void isolatedV025PreservesTextReplayPrivacyAndTrainingExclusion() {
        var service=service(review(),true);
        var saved=service.submit(owner,problem,"attempt-test-key",source,List.of("2*x = 8","x = 4"));
        assertThat(saved.status()).isEqualTo(AttemptStatus.ANALYZED);
        assertThat(saved.steps().getFirst().content()).isEqualTo("2*x = 8");
        assertThat(service.submit(owner,problem,"attempt-test-key",source,List.of("2*x = 8","x = 4"))).isEqualTo(saved);
        assertThatThrownBy(()->service.submit(owner,problem,"attempt-test-key",source,List.of("x=5"))).isInstanceOf(ApiProblemException.class);
        var privacy=new StudentAttemptPrivacyLifecycleContributor(store);
        var tx=new TransactionTemplate(transactions);
        assertThat(tx.execute(t->privacy.exportUserData(owner)).toString()).contains("2*x = 8","trainingEligible=false");
        assertThatThrownBy(()->jdbc.update("update student_attempts set training_eligible=true where id=?",saved.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("update student_attempt_steps set training_eligible=true where attempt_id=?",saved.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        tx.executeWithoutResult(t->{sources.lockOwner(owner,problem);privacy.deleteUserData(owner,Instant.now());});
        assertThat(store.history(owner,problem)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from student_attempt_steps where user_id=?",Integer.class,owner)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from problem_sessions where id=?",Integer.class,problem)).isEqualTo(1);
    }
    @Test void timeoutAndCrashRecoverExactAttemptWithoutTransactionDuringVerification() {
        var timeout=service((id,count,request)->{
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return AttemptAnalysisResult.unavailable(id,count,AttemptAnalysisResult.Reason.TIMEOUT);
        },true);
        var pending=timeout.submit(owner,problem,"recovery-test-key",source,List.of("x=4"));
        assertThat(pending.status()).isEqualTo(AttemptStatus.SUBMITTED);
        var recovered=service(review(),true).submit(owner,problem,"recovery-test-key",source,List.of("x=4"));
        assertThat(recovered.id()).isEqualTo(pending.id());assertThat(recovered.status()).isEqualTo(AttemptStatus.ANALYZED);
        assertThat(store.history(owner,problem)).hasSize(1);
    }
    @Test void sourceChangesDuringVerificationCannotPublish() {
        var changed=service((id,count,request)->{
            jdbc.update("update problem_sessions set current_parse_id=null where id=?",problem);
            return review().verify(id,count,request);
        },true).submit(owner,problem,"stale-test-key",source,List.of("x=4"));
        assertThat(changed.status()).isEqualTo(AttemptStatus.STALE_SOURCE);assertThat(changed.analysis()).isNull();
        assertThat(service(review(),false).read(owner,problem,changed.id()).steps()).hasSize(1);
    }
    @Test void concurrencyAndForeignOwnershipCannotDuplicateOrRead() throws Exception {
        var service=service(review(),true); var pool=Executors.newFixedThreadPool(3);
        try {
            var work=new ArrayList<Callable<StudentAttempt>>();
            for(int i=0;i<3;i++) work.add(()->service.submit(owner,problem,"concurrent-attempt",source,List.of("x=4")));
            var ids=new HashSet<UUID>();for(var f:pool.invokeAll(work)) ids.add(f.get(20,TimeUnit.SECONDS).id());
            assertThat(ids).hasSize(1);
            var foreign=new SolverSourceFixture(jdbc).insertUser();
            assertThatThrownBy(()->service.read(foreign,problem,ids.iterator().next())).isInstanceOf(ApiProblemException.class);
        } finally { pool.shutdownNow(); }
    }
    @Test void disabledHistoryRemainsAvailableAndStaleSubmissionRejects() {
        var saved=service(review(),true).submit(owner,problem,"before-disable",source,List.of("x=4"));
        var disabled=service(review(),false);
        assertThat(disabled.read(owner,problem,saved.id())).isEqualTo(saved);
        assertThat(disabled.submit(owner,problem,"before-disable",source,List.of("x=4"))).isEqualTo(saved);
        assertThatThrownBy(()->disabled.submit(owner,problem,"after-disable",source,List.of("x=4"))).isInstanceOf(ApiProblemException.class);
        var stale=new AttemptSourceIdentity(source.canonicalId(),source.canonicalRevision()+1,source.parseId(),source.parseRevision());
        assertThatThrownBy(()->service(review(),true).submit(owner,problem,"stale-submit",stale,List.of("x=4"))).isInstanceOf(ApiProblemException.class);
    }
    @Test void partialPublicationFailureRetainsOriginalSubmission() {
        var broken=service((id,count,request)->{ throw new IllegalStateException("synthetic worker crash"); },true);
        assertThatThrownBy(()->broken.submit(owner,problem,"crash-test-key",source,List.of("x=4"))).isInstanceOf(IllegalStateException.class);
        assertThat(store.history(owner,problem).getFirst().status()).isEqualTo(AttemptStatus.SUBMITTED);
        assertThat(service(review(),true).submit(owner,problem,"crash-test-key",source,List.of("x=4")).status()).isEqualTo(AttemptStatus.ANALYZED);
    }
    @Test void activeForwardMigrationsOwnTheAttemptLearningTables() {
        var versions=jdbc.queryForList("select version::integer from flyway_schema_history where success=true and version is not null",Integer.class);
        assertThat(versions).contains(30,31,32).doesNotContain(24,25,26);
        assertThat(jdbc.queryForObject("select to_regclass('learner_skill_mastery') is not null",Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select to_regclass('counterfactual_twin_drills') is not null",Boolean.class)).isTrue();
    }
    @Test void learningEvidenceIsIdempotentAndPrivacyDeletesDerivedMastery() {
        var attempt=service(review(),true).submit(owner,problem,"learning-evidence",source,List.of("x=5"));
        var learning=new com.verifiedai.attempt.infrastructure.persistence.AttemptLearningRepository(jdbc);
        var diagnosis=new AttemptAnalysisResult.Diagnosis(MistakeCode.ALG_FRESHMAN_DREAM_POWER,attempt.id()+":1",
            "attempt.mistake.ALG_FRESHMAN_DREAM_POWER","/expression","STEP_LEVEL");
        var result=new AttemptAnalysisResult("attempt-analysis-v2","bounded-attempt-v2","attempt-taxonomy-v2",attempt.id(),attempt.id().toString(),
            AttemptOutcome.STEP_ERROR_FOUND,1,"REAL_DOMAIN",List.of(new AttemptAnalysisResult.Step(1,StepVerdict.FIRST_WRONG_STEP,
                AttemptAnalysisResult.Reason.NON_EQUIVALENT,MistakeCode.ALG_FRESHMAN_DREAM_POWER,AttemptAnalysisResult.WitnessStatus.COUNTEREXAMPLE_UNAVAILABLE,null,diagnosis,null)),
            codec.object().put("kind","REALS"),"REVIEWED_REAL_TASK",null);
        var tx=new TransactionTemplate(transactions);
        tx.executeWithoutResult(t->{ sources.lockOwner(owner,problem); learning.attemptEvidence(owner,attempt.id(),result,Instant.now()); learning.attemptEvidence(owner,attempt.id(),result,Instant.now()); });
        assertThat(jdbc.queryForObject("select count(*) from learner_mistake_events where user_id=?",Integer.class,owner)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select evidence_count from learner_skill_mastery where user_id=?",Integer.class,owner)).isEqualTo(1);
        tx.executeWithoutResult(t->{ sources.lockOwner(owner,problem); store.delete(owner); });
        assertThat(jdbc.queryForObject("select count(*) from learner_skill_mastery where user_id=?",Integer.class,owner)).isZero();
    }
    @Test void reviewedAbsoluteValueSourceReachesV2WithoutRewritingLegacySource() {
        UUID modern=new SolverSourceFixture(jdbc).insertSupportedParse(owner,SolverSourceFixture.equationParseJson("abs(x)=2"),1);
        var document=canonical.canonicalize(owner,modern);
        assertThat(document.schemaVersion()).isEqualTo("canonical-problem-v3");
        var expected=service(review(),true).context(owner,modern).source();
        var capture=new java.util.concurrent.atomic.AtomicReference<String>();
        service((id,count,request)->{ capture.set(request); return review().verify(id,count,request); },true)
            .submit(owner,modern,"set-source-path",expected,List.of("x in {-2,2}"),true);
        var request=codec.parse(capture.get());
        assertThat(request.path("schemaVersion").asText()).isEqualTo("attempt-verification-v2");
        assertThat(request.path("domainBasis").asText()).isEqualTo("REVIEWED_REAL_TASK");
        assertThat(request.path("original").path("left").path("function").asText()).isEqualTo("ABS");
        assertThat(request.path("steps").get(0).path("statement").path("relation").asText()).isEqualTo("MEMBER_OF");
        assertThat(canonical.getCanonicalProblem(owner,problem).schemaVersion()).isEqualTo("canonical-problem-v1");
    }
    @Test void twinAnswerIsPrivateUntilCompletionAndRetriesCannotDoubleUpdateMastery() {
        var code=MistakeCode.ALG_SIGN_FLIP_DISTRIBUTE;
        AttemptVerifierClient synthetic=new AttemptVerifierClient() {
            public AttemptAnalysisResult verify(UUID id,int count,String request) {
                var diagnosis=new AttemptAnalysisResult.Diagnosis(code,id+":1","attempt.mistake."+code,"/","STEP_LEVEL");
                return new AttemptAnalysisResult("attempt-analysis-v2","bounded-attempt-v2","attempt-taxonomy-v2",id,id.toString(),
                    AttemptOutcome.STEP_ERROR_FOUND,1,"REAL_DOMAIN",List.of(new AttemptAnalysisResult.Step(1,StepVerdict.FIRST_WRONG_STEP,
                    AttemptAnalysisResult.Reason.NON_EQUIVALENT,code,AttemptAnalysisResult.WitnessStatus.COUNTEREXAMPLE_UNAVAILABLE,null,diagnosis,null)),
                    codec.object().put("kind","REALS"),"REVIEWED_REAL_TASK",null);
            }
            public AttemptLearningEvidence learning(UUID id,String action,String request) {
                assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                var options=List.of("2-x","-x-2","x+2","x-2").stream().map(text->codec.parse(normalizer.normalizeV2(text,Set.of("x")))).toList();
                var twin=new AttemptLearningEvidence.Twin("verified-twin-v1",code,
                    codec.parse(normalizer.normalizeV2("-(x-2)",Set.of("x"))),options,0,"attempt.mistake."+code,"x ∈ ℝ","FOUNDATION");
                return new AttemptLearningEvidence("attempt-learning-result-v1","bounded-learning-v1",id,id.toString(),action,"VERIFIED",twin,List.of());
            }
        };
        var attempt=service(synthetic,true).submit(owner,problem,"synthetic-drill-attempt",source,List.of("x=5"),true);
        var learningStore=new com.verifiedai.attempt.infrastructure.persistence.AttemptLearningRepository(jdbc);
        var learning=new AttemptLearningService(store,learningStore,sources,synthetic,codec,
            new com.verifiedai.tutoring.application.learning.TutorAssistanceReader(jdbc),transactions,Clock.systemUTC(),
            (p,k)->RateLimitDecision.allow(),new SimpleMeterRegistry(),true,reader);
        var open=learning.create(owner,problem,attempt.id(),"create-twin-key");
        assertThat(open.correctOption()).isNull(); assertThat(open.explanationKey()).isNull(); assertThat(open.status()).isEqualTo("OPEN");
        assertThat(learning.create(owner,problem,attempt.id(),"create-twin-key").id()).isEqualTo(open.id());
        var completed=learning.answer(owner,problem,attempt.id(),"answer-twin-key",0);
        assertThat(completed.correct()).isTrue(); assertThat(completed.correctOption()).isZero();
        assertThat(learning.answer(owner,problem,attempt.id(),"answer-twin-key",0)).isEqualTo(completed);
        assertThatThrownBy(()->learning.answer(owner,problem,attempt.id(),"answer-twin-key",1)).isInstanceOf(ApiProblemException.class);
        assertThat(jdbc.queryForObject("select count(*) from learner_mistake_events where user_id=?",Integer.class,owner)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select evidence_count from learner_skill_mastery where user_id=?",Integer.class,owner)).isEqualTo(2);
        var foreign=new SolverSourceFixture(jdbc).insertUser();
        assertThatThrownBy(()->learning.drill(foreign,problem,attempt.id())).isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(()->jdbc.update("update counterfactual_twin_drills set correct_option=1 where id=?",open.id()))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        var tx=new TransactionTemplate(transactions);
        assertThat(tx.execute(t->store.learningExport(owner)).toString()).doesNotContain("answer_key","answer-twin-key");
        tx.executeWithoutResult(t->{ sources.lockOwner(owner,problem); store.delete(owner); });
        assertThat(jdbc.queryForObject("select count(*) from counterfactual_twin_drills where user_id=?",Integer.class,owner)).isZero();
    }

    AttemptLearningService comparisonService(java.util.function.Consumer<UUID> during,boolean enabled) {
        AttemptVerifierClient client=new AttemptVerifierClient() {
            public AttemptAnalysisResult verify(UUID id,int count,String request) { return review().verify(id,count,request); }
            public MethodComparisonEvidence comparison(UUID id,String request) {
                assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                during.accept(id);
                return new MethodComparisonEvidence("method-comparison-result-v2","bounded-methods-v2",id,id.toString(),
                    "METHODS","UNSUPPORTED","UNSUPPORTED_SOURCE",codec.parse(request).path("source"),null,List.of());
            }
        };
        return new AttemptLearningService(store,new com.verifiedai.attempt.infrastructure.persistence.AttemptLearningRepository(jdbc),
            sources,client,codec,new com.verifiedai.tutoring.application.learning.TutorAssistanceReader(jdbc),transactions,
            Clock.systemUTC(),(p,k)->RateLimitDecision.allow(),new SimpleMeterRegistry(),enabled,reader);
    }
    @Test void sourceComparisonReplaysWithoutAttemptsOrDurableArtifacts() {
        var learning=comparisonService(id->{},true);
        var first=learning.comparison(owner,problem,null,source,"comparison-key-one");
        var replay=learning.comparison(owner,problem,null,source,"comparison-key-one");
        assertThat(replay).isEqualTo(first);
        assertThat(first.evidence().status()).isEqualTo("UNSUPPORTED");
        assertThat(first.evidence().methods()).isEmpty(); assertThat(first.trainingEligible()).isFalse();
        assertThat(store.history(owner,problem)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from attempt_verified_methods where user_id=?",Integer.class,owner)).isZero();
    }
    @Test void sourceComparisonRejectsStaleCrossOwnerAndDisabledRequests() {
        var calls=new java.util.concurrent.atomic.AtomicInteger(); var learning=comparisonService(id->calls.incrementAndGet(),true);
        assertThatThrownBy(()->learning.comparison(UUID.randomUUID(),problem,null,source,"comparison-owner")).isInstanceOf(ApiProblemException.class);
        var stale=new AttemptSourceIdentity(source.canonicalId(),source.canonicalRevision()+1,source.parseId(),source.parseRevision());
        assertThatThrownBy(()->learning.comparison(owner,problem,null,stale,"comparison-stale")).isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(()->comparisonService(id->calls.incrementAndGet(),false).comparison(owner,problem,null,source,"comparison-disabled")).isInstanceOf(ApiProblemException.class);
        assertThat(calls.get()).isZero();
    }
    @Test void sourceComparisonReauthorizesAfterRevocationAndSourceChange() {
        assertThatThrownBy(()->comparisonService(id->jdbc.update("update problem_sessions set current_parse_id=null where id=?",problem),true)
            .comparison(owner,problem,null,source,"comparison-source")).isInstanceOf(ApiProblemException.class);
    }
    @Test void sourceComparisonReauthorizesAccountStatus() {
        assertThatThrownBy(()->comparisonService(id->jdbc.update("delete from users where id=?",owner),true)
            .comparison(owner,problem,null,source,"comparison-revoked")).isInstanceOf(ApiProblemException.class);
    }
    @Test void concurrentSourceComparisonsHaveStableIdentityAndNoWrites() throws Exception {
        var barrier=new CyclicBarrier(2);
        var learning=comparisonService(id->{try { barrier.await(5,TimeUnit.SECONDS); } catch(Exception ex) { throw new IllegalStateException(ex); }},true);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var a=executor.submit(()->learning.comparison(owner,problem,null,source,"comparison-concurrent"));
            var b=executor.submit(()->learning.comparison(owner,problem,null,source,"comparison-concurrent"));
            assertThat(a.get(10,TimeUnit.SECONDS)).isEqualTo(b.get(10,TimeUnit.SECONDS));
        }
        assertThat(store.history(owner,problem)).isEmpty();
    }

    @Test void comparisonDoesNotOverwriteLegacyEvidenceAndPrivacyStillDeletesIt() {
        var saved=service(review(),true).submit(owner,problem,"comparison-history",source,List.of("x=4"),true);
        var learningStore=new com.verifiedai.attempt.infrastructure.persistence.AttemptLearningRepository(jdbc);
        var original=codec.parse(store.request(owner,saved.id())).path("original");
        var legacy=new AttemptLearningEvidence("attempt-learning-result-v1","bounded-learning-v1",saved.id(),saved.id().toString(),
            "METHODS","VERIFIED",null,List.of(new AttemptLearningEvidence.Method("SYMBOLIC_SIMPLIFICATION",List.of(original),"verified-method-v1")));
        var tx=new TransactionTemplate(transactions);
        tx.executeWithoutResult(t->{sources.lockOwner(owner,problem);learningStore.saveMethods(owner,saved.id(),legacy,Instant.now());});
        var comparison=comparisonService(id->{},true).comparison(owner,problem,saved.id(),source,"comparison-history-v2");
        assertThat(comparison.attemptId()).isEqualTo(saved.id());
        assertThat(learningStore.methods(owner,saved.id()).orElseThrow()).isEqualTo(legacy);
        assertThatThrownBy(()->jdbc.update("update attempt_verified_methods set evidence='{}'::jsonb where attempt_id=?",saved.id()))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        var privacy=new StudentAttemptPrivacyLifecycleContributor(store);
        assertThat(tx.execute(t->privacy.exportUserData(owner)).toString()).contains("verified-method-v1");
        tx.executeWithoutResult(t->{sources.lockOwner(owner,problem);privacy.deleteUserData(owner,Instant.now());});
        assertThat(learningStore.methods(owner,saved.id())).isEmpty();
    }

}
