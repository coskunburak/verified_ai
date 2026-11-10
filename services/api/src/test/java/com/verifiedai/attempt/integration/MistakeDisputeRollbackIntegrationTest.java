package com.verifiedai.attempt.integration;

import com.verifiedai.attempt.application.*;
import com.verifiedai.attempt.domain.*;
import com.verifiedai.attempt.infrastructure.persistence.*;
import com.verifiedai.mistake.*;
import com.verifiedai.mistake.infrastructure.*;
import com.verifiedai.sharedkernel.error.*;
import com.verifiedai.sharedkernel.ratelimit.RateLimitDecision;
import com.verifiedai.solving.SolverSourceFixture;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@TestPropertySource(properties={"verifiedai.mistake.dna-enabled=true","verifiedai.attempt.enabled=true","verifiedai.attempt.structured-work-enabled=true"})
@org.springframework.context.annotation.Import(MistakeDisputeRollbackIntegrationTest.SyntheticRateLimitConfiguration.class)
class MistakeDisputeRollbackIntegrationTest extends AttemptEvaluationIntegrationTest {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    static class SyntheticRateLimitConfiguration {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        com.verifiedai.sharedkernel.ratelimit.RateLimiter syntheticRateLimiter() {
            return (policy,key)->RateLimitDecision.allow();
        }
    }
    @Autowired AttemptMistakeRepository classifications;
    @Autowired AttemptMistakeClassifier classifier;
    @Autowired MistakeDnaRepository dnaStore;
    @Autowired MistakeDnaEvidenceSource dnaSource;
    @Autowired AttemptStepOcrCorrectionService corrections;
    static final String CODE="ALG_SIGN_FLIP_DISTRIBUTE", SKILL="MATH.ALGEBRA.SIMPLIFY_EXPRESSIONS";
    void tagSource() {
        UUID job=UUID.randomUUID(); String fingerprint=LearnerMistakeDnaService.hash(job.toString());
        jdbc.update("""
            insert into problem_classification_jobs(id,user_id,problem_session_id,canonical_problem_id,canonical_problem_revision,
                ontology_version,projection_version,status,capability,prompt_id,prompt_version,schema_version,route_policy_version,
                request_fingerprint,max_attempts,next_attempt_at,created_at,updated_at)
            values(?,?,?,?,?,'curriculum-v1-seed','projection-v1','SUCCEEDED','PROBLEM_CLASSIFY','synthetic','v1','problem-classification-v1',
                'synthetic',?,1,'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z','2026-01-01T00:00:00Z')
            """,job,owner,problem,source.canonicalId(),source.canonicalRevision(),fingerprint);
        jdbc.update("""
            insert into problem_classifications(id,classification_job_id,user_id,problem_session_id,canonical_problem_id,revision,source,
                status,ontology_version,classification_schema_version,projection_version,subject_id,topic_id,primary_skill_id,difficulty,
                difficulty_policy_version,confidence_band,confidence_policy_version,confidence_calibration,capability,prompt_id,prompt_version,
                route_policy_version,fallback_used,request_fingerprint,created_at)
            values(?,?,?,?,?,1,'SYSTEM','CLASSIFIED','curriculum-v1-seed','problem-classification-v1','projection-v1','MATH','MATH.ALGEBRA',?,
                'EASY','v1','HIGH','v1','UNCALIBRATED','PROBLEM_CLASSIFY','synthetic','v1','synthetic',false,?,'2026-01-01T00:00:00Z')
            """,UUID.randomUUID(),job,owner,problem,source.canonicalId(),SKILL,fingerprint);
    }
    AttemptAnalysisResult wrong(UUID id,int count,String request) {
        var witness=new CounterexampleEvidence("attempt-witness-v1","EQUATION_TRUTH",
            Map.of("x",new CounterexampleEvidence.ExactValue("4","1")),"true","false","EQUATION_EQUIVALENCE",true,"bounded-attempt-v2","DIFFERENT_TRUTH");
        var diagnosis=new AttemptAnalysisResult.Diagnosis(MistakeCode.ALG_SIGN_FLIP_DISTRIBUTE,id+":1","attempt.mistake."+CODE,"/","STEP_LEVEL","structural-signatures-v1");
        var steps=new ArrayList<AttemptAnalysisResult.Step>();
        steps.add(new AttemptAnalysisResult.Step(1,StepVerdict.FIRST_WRONG_STEP,AttemptAnalysisResult.Reason.NON_EQUIVALENT,
            MistakeCode.ALG_SIGN_FLIP_DISTRIBUTE,AttemptAnalysisResult.WitnessStatus.AVAILABLE,witness,diagnosis,null));
        for(int i=2;i<=count;i++) steps.add(new AttemptAnalysisResult.Step(i,StepVerdict.NOT_ASSESSED,AttemptAnalysisResult.Reason.EARLIER_DIVERGENCE,
            null,AttemptAnalysisResult.WitnessStatus.NOT_APPLICABLE,null));
        return new AttemptAnalysisResult("attempt-analysis-v2","bounded-attempt-v2","attempt-taxonomy-v2",id,id.toString(),
            AttemptOutcome.STEP_ERROR_FOUND,1,"REAL_DOMAIN",steps,codec.parse("{\"kind\":\"REALS\"}"),codec.parse(request).path("domainBasis").asText(),
            codec.parse("{\"kind\":\"FINITE_SET\",\"elements\":[\"4\"]}"));
    }
    AttemptMistakeClassification classify(AttemptStructuredWork work,AttemptVerifierClient verifier) {
        var evaluation=service(true,verifier).evaluate(owner,problem,work.attemptId(),UUID.randomUUID().toString(),
            new AttemptEvaluationInput("attempt-evaluation-request-v1",source,work.revision(),true)).evaluation();
        return new AttemptMistakeService(dna,sources,attempts,works,evaluations,classifications,classifier,transactions,
            Clock.systemUTC(),(p,k)->RateLimitDecision.allow(),metrics,true,true,"test",false)
            .classify(owner,problem,work.attemptId(),UUID.randomUUID().toString(),new AttemptMistakeInput("attempt-mistake-request-v1",evaluation.id(),source,work.revision())).classification();
    }
    LearnerMistakeDnaService.DisputeInput disputeInput(AttemptMistakeClassification c) {
        return new LearnerMistakeDnaService.DisputeInput("mistake-dispute-request-v1",c.evaluationId(),c.workRevision());
    }
    @Test void causalProjectionUsesVerifiedExposuresAndDisputeReversesRecurrence() {
        tagSource(); var first=classify(work(null,"x=5"),this::wrong); var second=classify(work(null,"x=5"),this::wrong);
        classify(work(null,"x=4"),this::proven); classify(work(null),this::unknown);
        var before=dna.read(owner,true).profiles().getFirst();
        assertThat(before.recentErrorCount()).isEqualTo(2); assertThat(before.recurrenceState()).isEqualTo(MistakeRecurrenceState.RECURRING_PATTERN);
        assertThat(before.decayedExposureCount().doubleValue()).isBetween(2.99,3.0);
        var original=classifications.get(owner,second.attemptId()).orElseThrow();
        var result=dna.dispute(owner,second.id(),"dispute-second",disputeInput(second));
        assertThat(result.status()).isEqualTo("EXCLUDED");
        assertThat(dna.dispute(owner,second.id(),"dispute-second",disputeInput(second))).isEqualTo(result);
        var after=dna.read(owner,true).profiles().getFirst();
        assertThat(after.recentErrorCount()).isEqualTo(1); assertThat(after.recurrenceState()).isEqualTo(MistakeRecurrenceState.OBSERVED_ONCE);
        assertThat(after.decayedExposureCount().doubleValue()).isBetween(1.99,2.0);
        assertThat(classifications.get(owner,second.attemptId())).contains(original);
        assertThat(jdbc.queryForObject("select count(*) from learner_skill_mastery where user_id=?",Integer.class,owner)).isZero();
        dna.dispute(owner,first.id(),"dispute-first",disputeInput(first)); assertThat(dna.read(owner,true).profiles()).isEmpty();
        assertThatThrownBy(()->jdbc.update("update learner_mistake_dispute_events set request_hash=? where id=?","0".repeat(64),result.operationId()))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("update attempt_mistake_classifications set created_at=now() where id=?",second.id()))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void tenDownstreamStepsStillProduceOneCausalEvent() {
        tagSource(); var steps=new ArrayList<AttemptWorkInput.Step>();
        for(int i=0;i<11;i++) steps.add(new AttemptWorkInput.Step(UUID.randomUUID(),"x=5",AttemptWorkInput.Role.INTERMEDIATE,List.of(),true));
        var work=capture().submit(owner,problem,"propagation-work",new AttemptWorkInput("attempt-structured-work-request-v1",source,
            AttemptWorkInput.Engine.MANUAL,null,null,null,null,List.of(),steps));
        classify(work,this::wrong);
        assertThat(dna.read(owner,true).profiles().getFirst().recentErrorCount()).isEqualTo(1);
        assertThat(dna.read(owner,true).entries()).hasSize(1);
    }
    @Test void unrelatedSkillAndWorkRevisionMismatchCannotEnterProjection() {
        tagSource(); jdbc.update("update problem_classifications set primary_skill_id='MATH.ARITHMETIC.FRACTIONS' where user_id=?",owner);
        var c=classify(work(null,"x=5"),this::wrong);
        assertThat(dna.read(owner,true).profiles()).isEmpty();
        assertThatThrownBy(()->dna.dispute(owner,c.id(),"revision-mismatch",new LearnerMistakeDnaService.DisputeInput("mistake-dispute-request-v1",c.evaluationId(),2)))
            .isInstanceOf(ApiProblemException.class);
    }
    @Test void missingSkillAndUncertainEvidenceNeverMutate() {
        classify(work(null,"x=5"),this::wrong); classify(work(null),this::unknown);
        assertThat(dna.read(owner,true).profiles()).isEmpty();
        assertThat(dna.read(owner,true).entries()).allMatch(e->!e.effectiveStatus().equals("EFFECTIVE"));
    }
    @Test void concurrentDisputesOwnershipKeyMismatchAndDeletionAreSafe() throws Exception {
        tagSource(); var c=classify(work(null,"x=5"),this::wrong); UUID foreign=new SolverSourceFixture(jdbc).insertUser();
        assertThatThrownBy(()->dna.dispute(foreign,c.id(),"foreign-dispute",disputeInput(c))).isInstanceOf(ApiProblemException.class);
        var pool=Executors.newFixedThreadPool(3);
        try {
            var tasks=new ArrayList<Callable<LearnerMistakeDnaService.Result>>();
            for(int i=0;i<3;i++) tasks.add(()->dna.dispute(owner,c.id(),"parallel-dispute",disputeInput(c)));
            var ids=new HashSet<UUID>(); for(var f:pool.invokeAll(tasks)) ids.add(f.get(15,TimeUnit.SECONDS).operationId());
            assertThat(ids).hasSize(1);
        } finally { pool.shutdownNow(); }
        assertThatThrownBy(()->dna.dispute(owner,c.id(),"parallel-dispute",new LearnerMistakeDnaService.DisputeInput("mistake-dispute-request-v1",UUID.randomUUID(),1))).isInstanceOf(ApiProblemException.class);
        assertThat(new MistakeDnaPrivacyLifecycleContributor(dnaStore).exportUserData(owner).toString()).contains("operations","DISPUTED_BY_LEARNER");
        privacy.requestDeletion(owner); privacy.confirmDeletion(owner,"DELETE");
        assertThat(jdbc.queryForObject("select count(*) from learner_mistake_dispute_events where user_id=?",Integer.class,owner)).isZero();
        assertThat(dnaStore.profiles(owner)).isEmpty();
        assertThatThrownBy(()->dna.dispute(owner,c.id(),"parallel-dispute",disputeInput(c))).isInstanceOf(ApiProblemException.class);
    }
    @Test void ocrCorrectionPreservesOriginalAndRequiresNewAcceptedEvidence() {
        tagSource(); UUID line=UUID.randomUUID();
        var work=capture().submit(owner,problem,"ocr-original-key",new AttemptWorkInput("attempt-structured-work-request-v1",source,
            AttemptWorkInput.Engine.NATIVE_VISION,UUID.randomUUID(),null,null,null,List.of(new AttemptWorkInput.Observation(line,"x=5",null,null)),
            List.of(new AttemptWorkInput.Step(UUID.randomUUID(),"x=5",AttemptWorkInput.Role.FINAL_ANSWER,List.of(line),true))));
        var c=classify(work,this::wrong);
        var input=new AttemptStepOcrCorrectionService.Input("attempt-ocr-correction-request-v1",source,1,"x=4");
        var result=corrections.correct(owner,problem,work.attemptId(),1,"ocr-correction-key",input);
        assertThat(result.operation().status()).isEqualTo("REEVALUATION_REQUIRED");
        assertThat(result.work().observations()).isEqualTo(work.observations());
        assertThat(works.get(owner,work.attemptId())).contains(work);
        assertThat(classifications.get(owner,work.attemptId())).contains(c);
        assertThat(dna.read(owner,true).profiles()).isEmpty();
        assertThat(corrections.correct(owner,problem,work.attemptId(),1,"ocr-correction-key",input)).isEqualTo(result);
        classify(result.work(),this::proven); assertThat(dna.read(owner,true).profiles()).isEmpty();
        assertThatThrownBy(()->corrections.correct(owner,problem,work.attemptId(),1,"ocr-correction-key",
            new AttemptStepOcrCorrectionService.Input("attempt-ocr-correction-request-v1",source,1,"x=6"))).isInstanceOf(ApiProblemException.class);
    }
    @Test void rollbackDisablementAndFullReplayNeverReactivateDisputedEvidence() {
        tagSource(); var c=classify(work(null,"x=5"),this::wrong); dna.dispute(owner,c.id(),"suspend-diagnosis",disputeInput(c));
        new TransactionTemplate(transactions).executeWithoutResult(t->dna.refresh(owner));
        assertThat(dna.read(owner,true).profiles()).isEmpty();
        var disabled=new LearnerMistakeDnaService(dnaStore,dnaSource,Clock.systemUTC(),metrics,(p,k)->RateLimitDecision.allow(),false,true,"test",false);
        var view=new TransactionTemplate(transactions).execute(t->disabled.read(owner,true));
        assertThat(view.enabled()).isFalse(); assertThat(view.profiles()).isEmpty();
        var diagnosis=new TransactionTemplate(transactions).execute(t->disabled.diagnosis(owner,c.id()));
        assertThat(diagnosis.enabled()).isFalse(); assertThat(diagnosis.effectiveStatus()).isEqualTo("DISABLED");
        var replay=new TransactionTemplate(transactions).execute(t->disabled.dispute(owner,c.id(),"suspend-diagnosis",disputeInput(c)));
        assertThat(replay.status()).isEqualTo("EXCLUDED");
    }
    @Test void duplicateCorrectionsAndOuterRollbackRemainAtomic() throws Exception {
        tagSource(); UUID line=UUID.randomUUID();
        var work=capture().submit(owner,problem,"parallel-ocr-original",new AttemptWorkInput("attempt-structured-work-request-v1",source,
            AttemptWorkInput.Engine.NATIVE_VISION,UUID.randomUUID(),null,null,null,List.of(new AttemptWorkInput.Observation(line,"x=5",null,null)),
            List.of(new AttemptWorkInput.Step(UUID.randomUUID(),"x=5",AttemptWorkInput.Role.FINAL_ANSWER,List.of(line),true))));
        var c=classify(work,this::wrong);
        new TransactionTemplate(transactions).executeWithoutResult(t->{ dna.dispute(owner,c.id(),"rolled-back-dispute",disputeInput(c)); t.setRollbackOnly(); });
        assertThat(dnaStore.excluded(owner)).isEmpty(); assertThat(dna.read(owner,true).profiles()).hasSize(1);
        var input=new AttemptStepOcrCorrectionService.Input("attempt-ocr-correction-request-v1",source,1,"x=4");
        var pool=Executors.newFixedThreadPool(3);
        try {
            var tasks=new ArrayList<Callable<AttemptStepOcrCorrectionService.Result>>();
            for(int i=0;i<3;i++) tasks.add(()->corrections.correct(owner,problem,work.attemptId(),1,"parallel-ocr-correction",input));
            var ids=new HashSet<UUID>(); for(var f:pool.invokeAll(tasks)) ids.add(f.get(15,TimeUnit.SECONDS).work().attemptId());
            assertThat(ids).hasSize(1); assertThat(works.export(owner)).hasSize(2);
        } finally { pool.shutdownNow(); }
        assertThat(dna.read(owner,true).profiles()).isEmpty();
    }
    @Test void staleAsyncEvaluationCannotRestoreCorrectedEvidence() throws Exception {
        UUID line=UUID.randomUUID();
        var work=capture().submit(owner,problem,"late-ocr-original",new AttemptWorkInput("attempt-structured-work-request-v1",source,
            AttemptWorkInput.Engine.NATIVE_VISION,UUID.randomUUID(),null,null,null,List.of(new AttemptWorkInput.Observation(line,"x=5",null,null)),
            List.of(new AttemptWorkInput.Step(UUID.randomUUID(),"x=5",AttemptWorkInput.Role.INTERMEDIATE,List.of(line),true))));
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1); var pool=Executors.newSingleThreadExecutor();
        try {
            var slow=service(true,(id,n,request)->{
                entered.countDown();
                try { if(!release.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Synthetic timeout"); }
                catch(InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                return wrong(id,n,request);
            });
            var response=pool.submit(()->slow.evaluate(owner,problem,work.attemptId(),"late-evaluation-key",
                new AttemptEvaluationInput("attempt-evaluation-request-v1",source,1,true)));
            assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            corrections.correct(owner,problem,work.attemptId(),1,"late-ocr-correction",
                new AttemptStepOcrCorrectionService.Input("attempt-ocr-correction-request-v1",source,1,"x=4"));
            release.countDown(); assertThat(response.get(15,TimeUnit.SECONDS).historical()).isTrue();
            assertThat(evaluations.get(owner,work.attemptId()).orElseThrow().artifact()).isNull();
            assertThat(dna.read(owner,true).profiles()).isEmpty();
        } finally { release.countDown(); pool.shutdownNow(); }
    }
    @Test void unknownSkillOntologyFailsClosedAndFreeReadNeverLeaksHistory() {
        tagSource(); jdbc.update("update problem_classifications set ontology_version='unknown-version' where user_id=?",owner);
        classify(work(null,"x=5"),this::wrong);
        assertThat(dna.read(owner,true).profiles()).isEmpty();
        assertThat(dna.read(owner,false).entries()).isEmpty();
        assertThat(dna.read(owner,false).limited()).isTrue();
    }
    @Test void fixedClockReplayDatabaseUniquenessAndRetentionInvalidation() {
        tagSource(); var c=classify(work(null,"x=5"),this::wrong);
        var fixed=new LearnerMistakeDnaService(dnaStore,dnaSource,Clock.fixed(Instant.now().plusSeconds(1),ZoneOffset.UTC),metrics,
            (p,k)->RateLimitDecision.allow(),true,true,"test",false);
        var tx=new TransactionTemplate(transactions);
        var first=tx.execute(t->fixed.read(owner,true)); var second=tx.execute(t->fixed.read(owner,true));
        assertThat(second).isEqualTo(first);
        var result=dna.dispute(owner,c.id(),"unique-dispute-key",disputeInput(c));
        assertThatThrownBy(()->jdbc.update("""
            insert into learner_mistake_dispute_events(id,user_id,attempt_id,classification_id,dispute_type,create_key,request_hash,created_at)
            select ?,user_id,attempt_id,classification_id,dispute_type,'different-key',request_hash,created_at
            from learner_mistake_dispute_events where id=?
            """,UUID.randomUUID(),result.operationId())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("delete from learner_mistake_dispute_events where id=?",result.operationId()))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        classify(work(null,"x=5"),this::wrong); assertThat(dnaStore.profiles(owner)).hasSize(1);
        tx.executeWithoutResult(t->{sources.lockOwner(owner,problem);jdbc.update("delete from problem_sessions where id=? and user_id=?",problem,owner);});
        assertThat(dnaStore.profiles(owner)).isEmpty(); assertThat(dnaStore.excluded(owner)).isEmpty();
    }
    @Test void rollbackLatencyAtExistingCapacity() {
        tagSource(); var values=new ArrayList<AttemptMistakeClassification>();
        for(int i=0;i<30;i++) values.add(classify(work(null,"x=5"),this::wrong));
        var durations=new ArrayList<Long>();
        for(int i=0;i<20;i++) {
            var c=values.get(i); long start=System.nanoTime();
            dna.dispute(owner,c.id(),"latency-dispute-"+i,disputeInput(c)); durations.add(System.nanoTime()-start);
        }
        Collections.sort(durations); double p95=durations.get(18)/1_000_000.0;
        System.out.println("MISTAKE_DNA_ROLLBACK_P95_MS="+p95);
        assertThat(p95).as("Synthetic local 30-attempt capacity; staging remains a separate gate").isLessThan(250.0);
    }
    @Test void upgradeFromV036PreservesClassificationAndAddsEmptyProjections() throws Exception {
        var work=work(null); var c=classify(work,this::unknown); var evaluation=evaluations.get(owner,work.attemptId()).orElseThrow().artifact();
        String schema="s75_"+UUID.randomUUID().toString().replace("-","");
        try {
            org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration/platform").target("36").load().migrate();
            try(var connection=dataSource.getConnection()) {
                var isolated=new org.springframework.jdbc.core.JdbcTemplate(
                    new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true));
                String prior=connection.getSchema(); boolean autoCommit=connection.getAutoCommit();
                try {
                    connection.setSchema(schema);
                    // Preserve the same-transaction capture invariant while copying only synthetic fixtures.
                    connection.setAutoCommit(false);
                    isolated.update("insert into users select * from public.users where id=?",owner);
                    isolated.update("insert into problem_sessions(id,user_id,status,input_mode,created_at,updated_at) select id,user_id,status,input_mode,created_at,updated_at from public.problem_sessions where id=?",problem);
                    for(String table:List.of("problem_assets","problem_asset_derivatives","recognition_jobs","recognition_evidence","problem_parse_jobs","problem_parses","canonical_problems"))
                        isolated.update("insert into "+table+" select * from public."+table+" where user_id=?",owner);
                    isolated.update("insert into student_attempts select * from public.student_attempts where id=?",work.attemptId());
                    for(String table:List.of("student_attempt_steps","attempt_structured_work","attempt_evaluations","attempt_mistake_classifications"))
                        isolated.update("insert into "+table+" select * from public."+table+" where attempt_id=?",work.attemptId());
                    connection.commit(); connection.setAutoCommit(true);
                    String attemptBefore=isolated.queryForObject("select to_jsonb(a)::text from student_attempts a where id=?",String.class,work.attemptId());
                    String evaluationBefore=isolated.queryForObject("select to_jsonb(e)::text from attempt_evaluations e where id=?",String.class,evaluation.id());
                    org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                        .locations("classpath:db/migration/platform").load().migrate();
                    assertThat(isolated.queryForObject("select to_jsonb(a)::text from student_attempts a where id=?",String.class,work.attemptId())).isEqualTo(attemptBefore);
                    assertThat(isolated.queryForObject("select to_jsonb(e)::text from attempt_evaluations e where id=?",String.class,evaluation.id())).isEqualTo(evaluationBefore);
                    assertThat(new AttemptWorkRepository(isolated).get(owner,work.attemptId())).contains(work);
                    assertThat(new AttemptEvaluationRepository(isolated).get(owner,work.attemptId()).orElseThrow().artifact()).isEqualTo(evaluation);
                    assertThat(new AttemptMistakeRepository(isolated,event -> { }).export(owner)).containsExactly(c);
                    assertThat(isolated.queryForObject("select count(*) from learner_mistake_dna_profiles",Integer.class)).isZero();
                    assertThat(isolated.queryForObject("select count(*) from learner_mistake_dispute_events",Integer.class)).isZero();
                } finally {
                    if(!connection.getAutoCommit()) connection.rollback();
                    connection.setAutoCommit(autoCommit); connection.setSchema(prior);
                }
            }
        } finally { jdbc.execute("drop schema if exists "+schema+" cascade"); }
    }
}
