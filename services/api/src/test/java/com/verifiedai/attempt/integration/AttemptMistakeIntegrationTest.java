package com.verifiedai.attempt.integration;

import com.verifiedai.attempt.application.*;
import com.verifiedai.attempt.domain.*;
import com.verifiedai.attempt.infrastructure.persistence.*;
import com.verifiedai.attempt.infrastructure.privacy.StudentAttemptPrivacyLifecycleContributor;
import com.verifiedai.sharedkernel.error.*;
import com.verifiedai.sharedkernel.ratelimit.*;
import com.verifiedai.solving.SolverSourceFixture;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/** Reuses the synthetic 7.3 capture/evaluation fixture, not prior acceptance results. */
class AttemptMistakeIntegrationTest extends AttemptEvaluationIntegrationTest {
    @Autowired AttemptMistakeRepository classifications;
    @Autowired AttemptMistakeClassifier classifier;
    AttemptMistakeService classification(boolean enabled) {
        return new AttemptMistakeService(sources,attempts,works,evaluations,classifications,classifier,transactions,
            Clock.systemUTC(),(p,k)->RateLimitDecision.allow(),metrics,enabled,true,"test",false);
    }
    AttemptEvaluation accepted(AttemptStructuredWork work) {
        return service(true,this::unknown).evaluate(owner,problem,work.attemptId(),UUID.randomUUID().toString(),input(work)).evaluation();
    }
    AttemptMistakeInput command(AttemptEvaluation e) { return new AttemptMistakeInput("attempt-mistake-request-v1",e.id(),e.source(),e.workRevision()); }
    @Test void classificationIsImmutableExportableAndDeletedWithoutMasterySideEffects() {
        var work=work(null); var e=accepted(work);
        var value=classification(true).classify(owner,problem,work.attemptId(),"classification-key",command(e)).classification();
        assertThat(value.decision().status().name()).isEqualTo("UNCERTAIN");
        assertThat(classifications.export(owner)).containsExactly(value);
        assertThat(evaluations.get(owner,work.attemptId()).orElseThrow().artifact()).isEqualTo(e);
        assertThat(new StudentAttemptPrivacyLifecycleContributor(attempts).exportUserData(owner).toString()).contains("mistakeClassifications","trainingEligible=false");
        for(String sql:List.of("update attempt_mistake_classifications set training_eligible=true where id=?",
            "update attempt_mistake_classifications set artifact='{}'::jsonb where id=?","delete from attempt_mistake_classifications where id=?"))
            assertThatThrownBy(()->jdbc.update(sql,value.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select count(*) from learner_mistake_events where user_id=?",Integer.class,owner)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from learner_skill_mastery where user_id=?",Integer.class,owner)).isZero();
        privacy.requestDeletion(owner); privacy.confirmDeletion(owner,"DELETE");
        assertThat(classifications.export(owner)).isEmpty();
        assertThatThrownBy(()->classification(true).read(owner,problem,work.attemptId())).isInstanceOf(ApiProblemException.class);
    }
    @Test void confirmedDisproofPersistsCausalEvidenceWithoutInventingACategory() {
        var work=work(null,"x=5");
        AttemptVerifierClient disproof=(id,count,request)->{
            var witness=new CounterexampleEvidence("attempt-witness-v1","EQUATION_TRUTH",
                Map.of("x",new CounterexampleEvidence.ExactValue("4","1")),"true","false","EQUATION_EQUIVALENCE",true,"bounded-attempt-v2","DIFFERENT_TRUTH");
            var diagnosis=new AttemptAnalysisResult.Diagnosis(MistakeCode.INVALID_TRANSFORMATION,id+":1",
                "attempt.mistake.INVALID_TRANSFORMATION","/","STEP_LEVEL","structural-signatures-v1");
            var step=new AttemptAnalysisResult.Step(1,StepVerdict.FIRST_WRONG_STEP,AttemptAnalysisResult.Reason.NON_EQUIVALENT,
                MistakeCode.INVALID_TRANSFORMATION,AttemptAnalysisResult.WitnessStatus.AVAILABLE,witness,diagnosis,null);
            return new AttemptAnalysisResult("attempt-analysis-v2","bounded-attempt-v2","attempt-taxonomy-v2",id,id.toString(),
                AttemptOutcome.STEP_ERROR_FOUND,1,"REAL_DOMAIN",List.of(step),codec.parse("{\"kind\":\"REALS\"}"),
                codec.parse(request).path("domainBasis").asText(),codec.parse("{\"kind\":\"FINITE_SET\",\"elements\":[\"4\"]}"));
        };
        var evaluation=service(true,disproof).evaluate(owner,problem,work.attemptId(),"proven-evaluation",
            new AttemptEvaluationInput("attempt-evaluation-request-v1",source,work.revision(),true)).evaluation();
        var result=classification(true).classify(owner,problem,work.attemptId(),"proven-classification",command(evaluation)).classification();
        assertThat(result.firstWrongStepId()).isEqualTo(work.steps().getFirst().id());
        assertThat(result.supportingEvidenceId()).isEqualTo(evaluation.executionId()+":1");
        assertThat(result.decision().evidenceStrength().name()).isEqualTo("EXACT_DISPROOF");
        assertThat(result.decision().category()).isNull();
        assertThat(classification(false).read(owner,problem,work.attemptId()).classification()).isEqualTo(result);
    }
    @Test void concurrentIdenticalRequestsAndConflictingKeys() throws Exception {
        var work=work(null); var e=accepted(work); var service=classification(true);
        var pool=Executors.newFixedThreadPool(4); var start=new CountDownLatch(1);
        try {
            var results=new ArrayList<Future<AttemptMistakeService.View>>();
            for(int i=0;i<4;i++) results.add(pool.submit(()->{ start.await(); return service.classify(owner,problem,work.attemptId(),"parallel-classification",command(e)); }));
            start.countDown(); UUID id=results.getFirst().get(15,TimeUnit.SECONDS).classification().id();
            for(var future:results) assertThat(future.get(15,TimeUnit.SECONDS).classification().id()).isEqualTo(id);
            assertThat(classifications.export(owner)).hasSize(1);
            assertThatThrownBy(()->service.classify(owner,problem,work.attemptId(),"another-classification",command(e)))
                .isInstanceOfSatisfying(ApiProblemException.class,x->assertThat(x.code()).isEqualTo(ApiErrorCode.IDEMPOTENCY_KEY_REUSED));
            var other=work(null); var otherEvaluation=accepted(other);
            assertThatThrownBy(()->service.classify(owner,problem,other.attemptId(),"parallel-classification",command(otherEvaluation))).isInstanceOf(ApiProblemException.class);
        } finally { pool.shutdownNow(); }
    }
    @Test void forgedForeignMissingEvidenceAndStaleSourceAreRejected() {
        var work=work(null);
        assertThatThrownBy(()->classification(true).classify(owner,problem,work.attemptId(),"missing-evidence",new AttemptMistakeInput("attempt-mistake-request-v1",UUID.randomUUID(),source,1)))
            .isInstanceOfSatisfying(ApiProblemException.class,x->assertThat(x.code()).isEqualTo(ApiErrorCode.ATTEMPT_CLASSIFICATION_NOT_READY));
        var e=accepted(work); var stranger=new SolverSourceFixture(jdbc).insertUser();
        assertThatThrownBy(()->classification(true).read(stranger,problem,work.attemptId())).isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(()->classification(true).classify(stranger,problem,work.attemptId(),"foreign-evidence",command(e))).isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(()->classification(true).classify(owner,problem,work.attemptId(),"forged-evidence",new AttemptMistakeInput("attempt-mistake-request-v1",UUID.randomUUID(),source,1))).isInstanceOf(ApiProblemException.class);
        jdbc.update("update problem_sessions set current_parse_id=null where id=?",problem);
        assertThatThrownBy(()->classification(true).classify(owner,problem,work.attemptId(),"stale-evidence",command(e)))
            .isInstanceOfSatisfying(ApiProblemException.class,x->assertThat(x.code()).isEqualTo(ApiErrorCode.ATTEMPT_STALE_SOURCE));
        assertThat(classifications.export(owner)).isEmpty();
    }
    @Test void rollbackDisablementPreservesHistoryAndExactReplay() {
        var work=work(null); var e=accepted(work);
        assertThatThrownBy(()->classification(false).classify(owner,problem,work.attemptId(),"rollout-disabled",command(e))).isInstanceOf(ApiProblemException.class);
        var saved=classification(true).classify(owner,problem,work.attemptId(),"rollout-disabled",command(e)).classification();
        work(work.attemptId());
        var read=classification(false).read(owner,problem,work.attemptId());
        assertThat(read.historical()).isTrue(); assertThat(read.creationEnabled()).isFalse(); assertThat(read.classification()).isEqualTo(saved);
        assertThat(classification(false).classify(owner,problem,work.attemptId(),"rollout-disabled",command(e)).classification()).isEqualTo(saved);
        var newer=work(null); var evaluation=accepted(newer); work(newer.attemptId());
        assertThatThrownBy(()->classification(true).classify(owner,problem,newer.attemptId(),"superseded-evidence",command(evaluation))).isInstanceOf(ApiProblemException.class);
    }
    @Test void transactionRollbackAndSessionCascadeDoNotLeavePartialPublication() {
        var work=work(null); var e=accepted(work);
        new TransactionTemplate(transactions).executeWithoutResult(t->{
            classification(true).classify(owner,problem,work.attemptId(),"rollback-atomicity",command(e)); t.setRollbackOnly();
        });
        assertThat(classifications.export(owner)).isEmpty();
        classification(true).classify(owner,problem,work.attemptId(),"rollback-atomicity",command(e));
        new TransactionTemplate(transactions).executeWithoutResult(t->{ sources.lockOwner(owner,problem); jdbc.update("delete from problem_sessions where id=? and user_id=?",problem,owner); });
        assertThat(classifications.export(owner)).isEmpty();
    }
    @Test void databaseRejectsMismatchedEvaluationAndForgedProof() throws Exception {
        var work=work(null); var e=accepted(work); var result=classifier.classify(e,Instant.now());
        var other=accepted(work(null)); var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        var json=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.valueToTree(result);
        json.put("evaluationId",other.id().toString());
        assertThatThrownBy(()->jdbc.update("insert into attempt_mistake_classifications(id,user_id,problem_session_id,attempt_id,evaluation_id,policy_version,create_key,created_at,artifact) values(?,?,?,?,?,'evidence-classifier-v1','forged-database',?::timestamptz,?::jsonb)",
            result.id(),owner,problem,work.attemptId(),other.id(),result.createdAt(),json.toString())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        json.put("evaluationId",e.id().toString()).put("firstWrongStepIndex",1).put("firstWrongStepId",work.steps().getFirst().id().toString()).put("supportingEvidenceId",e.executionId()+":1");
        ((com.fasterxml.jackson.databind.node.ObjectNode)json.path("decision")).put("evidenceStrength","EXACT_DISPROOF").put("code","INVALID_TRANSFORMATION");
        assertThatThrownBy(()->jdbc.update("insert into attempt_mistake_classifications(id,user_id,problem_session_id,attempt_id,evaluation_id,policy_version,create_key,created_at,artifact) values(?,?,?,?,?,'evidence-classifier-v1','forged-database',?::timestamptz,?::jsonb)",
            result.id(),owner,problem,work.attemptId(),e.id(),result.createdAt(),json.toString())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void classificationTelemetryIsBoundedAndProductionFlagNeedsAuthorization() {
        var work=work(null); var e=accepted(work);
        classification(true).classify(owner,problem,work.attemptId(),"metrics-classification",command(e));
        for(var meter:metrics.getMeters()) for(var tag:meter.getId().getTags())
            assertThat(tag.getValue()).doesNotContain(owner.toString(),work.attemptId().toString(),"x=4");
        var limited=new AttemptMistakeService(sources,attempts,works,evaluations,classifications,classifier,transactions,
            Clock.systemUTC(),(p,k)->RateLimitDecision.denied(60),metrics,true,true,"test",false);
        assertThatThrownBy(()->limited.read(owner,problem,work.attemptId()))
            .isInstanceOfSatisfying(ApiProblemException.class,x->assertThat(x.code()).isEqualTo(ApiErrorCode.RATE_LIMIT_EXCEEDED));
        assertThatThrownBy(()->new AttemptMistakeService(sources,attempts,works,evaluations,classifications,classifier,transactions,
            Clock.systemUTC(),(p,k)->RateLimitDecision.allow(),metrics,true,true,"production",false)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void upgradeFromV035AddsNoBackfillAndPreservesOldEvidence() throws Exception {
        var work=work(null); var evaluation=accepted(work);
        String schema="s74_"+UUID.randomUUID().toString().replace("-","");
        try {
            org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration/platform").target("35").load().migrate();
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
                    for(String table:List.of("student_attempt_steps","attempt_structured_work","attempt_evaluations"))
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
                    assertThat(new AttemptMistakeRepository(isolated).export(owner)).isEmpty();
                } finally {
                    if(!connection.getAutoCommit()) connection.rollback();
                    connection.setAutoCommit(autoCommit); connection.setSchema(prior);
                }
            }
        } finally { jdbc.execute("drop schema if exists "+schema+" cascade"); }
    }
}
