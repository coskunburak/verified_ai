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


import com.verifiedai.mistake.*;
import com.verifiedai.mistake.infrastructure.*;
import com.verifiedai.mastery.*;
import com.verifiedai.mastery.infrastructure.*;
import com.verifiedai.sharedkernel.contracts.VersionedJsonContract;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/** Existing synthetic source/evaluation/classification fixtures; all SQL runs in disposable PostgreSQL. */
@TestPropertySource(properties={"verifiedai.mastery.decay-reassessment-enabled=true","verifiedai.mastery.evidence-stratified-enabled=true","verifiedai.mistake.dna-enabled=true",
    "verifiedai.attempt.enabled=true","verifiedai.attempt.structured-work-enabled=true"})
@Import({StudentAttemptIntegrationTest.StorageConfiguration.class, MistakeDisputeRollbackIntegrationTest.SyntheticRateLimitConfiguration.class, EvidenceStratifiedMasteryIntegrationTest.FixedClockConfiguration.class})
class EvidenceStratifiedMasteryIntegrationTest extends PostgresIntegrationTestSupport {
    static final Instant TEST_AT=Instant.parse("2035-01-01T00:00:00Z");
    static final Clock TEST_CLOCK=Clock.fixed(TEST_AT,ZoneOffset.UTC);
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    static class FixedClockConfiguration {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        Clock masteryTestClock() { return TEST_CLOCK; }
    }
    @Autowired com.verifiedai.mistake.LearnerMistakeDnaService dna;
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
    AttemptWorkService capture() { return capture(TEST_CLOCK); }
    AttemptWorkService capture(Clock when) {
        return new AttemptWorkService(dna,sources,attempts,works,normalizer,parser,extraction,reader,codec,metrics,
            (p,k)->RateLimitDecision.allow(),when,transactions,true,true,"test",false);
    }
    AttemptStructuredWork work(UUID prior) { return work(prior,"x=4"); }
    AttemptStructuredWork work(UUID prior,String text) {
        return work(prior,text,TEST_CLOCK);
    }
    AttemptStructuredWork work(UUID prior,String text,Clock when) {
        return capture(when).submit(owner,problem,UUID.randomUUID().toString(),new AttemptWorkInput("attempt-structured-work-request-v1",source,
            AttemptWorkInput.Engine.MANUAL,null,null,null,prior,List.of(),List.of(new AttemptWorkInput.Step(UUID.randomUUID(),text,AttemptWorkInput.Role.FINAL_ANSWER,List.of(),true))));
    }
    AttemptEvaluationService service(boolean enabled,AttemptVerifierClient verifier) {
        return new AttemptEvaluationService(sources,attempts,works,evaluations,new AttemptEvaluationPreparer(codec,reader),verifier,
            transactions,TEST_CLOCK,(p,k)->RateLimitDecision.allow(),metrics,enabled,true,"test",false);
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
            TEST_CLOCK,(p,k)->RateLimitDecision.allow(),metrics,true,true,"test",false)
            .classify(owner,problem,work.attemptId(),UUID.randomUUID().toString(),new AttemptMistakeInput("attempt-mistake-request-v1",evaluation.id(),source,work.revision())).classification();
    }
    LearnerMistakeDnaService.DisputeInput disputeInput(AttemptMistakeClassification c) {
        return new LearnerMistakeDnaService.DisputeInput("mistake-dispute-request-v1",c.evaluationId(),c.workRevision());
    }

    @Autowired MasteryApplicationService mastery;
    @Autowired MasteryRepository masteryStore;
    @Autowired MasteryPrivacyLifecycleContributor masteryPrivacy;
    @Autowired MasteryEvidenceSource masterySource;
    int auditCount() { return jdbc.queryForObject("select count(*) from learner_skill_mastery_transitions where user_id=?",Integer.class,owner); }
    @Test void acceptedEvaluationProducesCanonicalMasteryAtomicAuditAndContract() throws Exception {
        tagSource(); var work=work(null); classify(work,this::proven);
        var view=mastery.read(owner); var skill=view.skills().getFirst();
        assertThat(skill.canonicalSkillCode()).isEqualTo(SKILL);
        assertThat(skill.posteriorMastery()).isEqualTo(.72666667);
        assertThat(skill.independentEvidenceCount()).isEqualTo(1);
        assertThat(skill.independentCompetence()).isBetween(.43749,.43751);
        assertThat(view.outcomes().getFirst().evidenceType()).isEqualTo(LearningEvidenceType.INDEPENDENT);
        var audit=jdbc.queryForMap("select * from learner_skill_mastery_transitions where user_id=?",owner);
        assertThat(((Number)audit.get("prior_score")).doubleValue()).isEqualTo(.25);
        assertThat(((Number)audit.get("posterior_score")).doubleValue()).isEqualTo(.72666667);
        assertThat(audit.get("evaluation_id")).isNotNull(); assertThat(audit.get("skill_evidence_id")).isNotNull();
        var time=new com.fasterxml.jackson.databind.module.SimpleModule();
        time.addSerializer(Instant.class,new com.fasterxml.jackson.databind.JsonSerializer<>() {
            @Override public void serialize(Instant value,com.fasterxml.jackson.core.JsonGenerator out,com.fasterxml.jackson.databind.SerializerProvider c) throws java.io.IOException { out.writeString(value.toString()); }
        });
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper().registerModule(time);
        VersionedJsonContract.validate("mastery-skills-v1",mapper.writeValueAsString(view));
    }
    @Test void duplicatesReplayAndConcurrentSameSkillReadsHaveOneAudit() throws Exception {
        tagSource(); classify(work(null),this::proven);
        int count=auditCount();
        var executor=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            var a=executor.submit(()->{start.await(); return mastery.read(owner);});
            var b=executor.submit(()->{start.await(); return mastery.read(owner);}); start.countDown();
            assertThat(a.get(10,TimeUnit.SECONDS).skills().getFirst().posteriorMastery()).isEqualTo(b.get(10,TimeUnit.SECONDS).skills().getFirst().posteriorMastery());
        } finally { executor.shutdownNow(); }
        for(int i=0;i<4;i++) mastery.read(owner);
        assertThat(auditCount()).isEqualTo(count);
        assertThatThrownBy(()->jdbc.update("update learner_skill_mastery_transitions set posterior_score=.5 where user_id=?",owner))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(jdbc.update("""
            insert into learner_skill_mastery_transitions select gen_random_uuid(),user_id,canonical_skill_code,attempt_id,evidence_id,
            evaluation_id,execution_id,skill_evidence_id,evidence_type,prior_score,posterior_score,prior_half_life_days,posterior_half_life_days,
            policy_version,evidence_fingerprint,provenance,event_time,created_at,training_eligible
            from learner_skill_mastery_transitions where user_id=? on conflict do nothing
            """,owner)).isZero();
    }
    @Test void concurrentAcceptedAttemptsAreNotLost() throws Exception {
        tagSource(); var first=work(null); var second=work(null);
        var executor=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            var a=executor.submit(()->{start.await(); return classify(first,this::proven);});
            var b=executor.submit(()->{start.await(); return classify(second,this::proven);}); start.countDown();
            a.get(15,TimeUnit.SECONDS); b.get(15,TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
        assertThat(mastery.read(owner).skills().getFirst().independentEvidenceCount()).isEqualTo(2);
    }
    @Test void missingLineageUnsupportedSkillAndUncertainEvaluationDoNotCreateState() {
        var noSkill=work(null); classify(noSkill,this::proven);
        assertThat(mastery.read(owner).skills()).isEmpty();
    }
    @Test void unsupportedOutcomeDoesNotCreateState() {
        tagSource(); var unknown=work(null); classify(unknown,this::unknown);
        assertThat(mastery.read(owner).skills()).isEmpty();
    }
    @Test void missingContextDoesNotCreateState() {
        tagSource(); var missingContext=work(null);
        jdbc.update("delete from attempt_mastery_context where evidence_id=?",missingContext.attemptId());
        classify(missingContext,this::proven);
        assertThat(mastery.read(owner).skills()).isEmpty(); assertThat(auditCount()).isZero();
    }
    @Test void solutionDeliveryIsCapturedBeforeSubmitAndCannotInflateMastery() {
        tagSource();
        jdbc.update("insert into solution_learning_disclosures(user_id,problem_session_id,first_delivered_at,tracking_complete) values(?,?,?,true)",
            owner,problem,java.sql.Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
        classify(work(null),this::proven);
        var view=mastery.read(owner);
        assertThat(view.outcomes().getFirst().evidenceType()).isEqualTo(LearningEvidenceType.SOLUTION_REVEALED_ASSISTED);
        assertThat(view.skills().getFirst().posteriorMastery()).isEqualTo(.05);
        assertThat(view.skills().getFirst().evidenceConfidence()).isZero();
    }
    @Test void disputedAndCorrectedEvidenceInvalidatesCacheThenRebuildsWithoutFalseState() {
        tagSource(); var first=work(null); var c=classify(first,this::proven);
        assertThat(mastery.read(owner).skills()).hasSize(1);
        dna.dispute(owner,c.id(),"mastery-dispute",disputeInput(c));
        assertThat(jdbc.queryForObject("select count(*) from learner_skill_mastery where user_id=? and mastery_version='bkt-lite-v2'",Integer.class,owner)).isZero();
        assertThat(mastery.read(owner).skills()).isEmpty();
        assertThat(auditCount()).isEqualTo(1); // Historical audit retained, never treated as new evidence.
        var second=work(null); classify(second,this::proven);
        work(second.attemptId());
        assertThat(mastery.read(owner).skills()).isEmpty();
    }
    @Test void outerTransactionRollbackRevertsProjectionAndAuditTogether() {
        tagSource(); var work=work(null);
        var evaluation=service(true,this::proven).evaluate(owner,problem,work.attemptId(),"mastery-rollback-eval",
            new AttemptEvaluationInput("attempt-evaluation-request-v1",source,1,true)).evaluation();
        var classification=classifier.classify(evaluation,work,TEST_AT);
        new TransactionTemplate(transactions).executeWithoutResult(t->{
            sources.lockOwner(owner,problem);
            classifications.insert("mastery-rollback-classify",classification,evaluation);
            assertThat(auditCount()).isEqualTo(1); t.setRollbackOnly();
        });
        assertThat(auditCount()).isZero(); assertThat(mastery.read(owner).skills()).isEmpty();
    }
    @Test void ownerIsolationExportDeletionAndDisabledFlag() {
        tagSource(); classify(work(null),this::proven);
        UUID other=new com.verifiedai.solving.SolverSourceFixture(jdbc).insertUser();
        assertThat(mastery.read(other).skills()).isEmpty();
        assertThat(masteryPrivacy.exportUserData(owner).get("transitions")).asList().hasSize(1);
        var disabled=new MasteryApplicationService(masteryStore,masterySource,TEST_CLOCK,metrics,(p,k)->com.verifiedai.sharedkernel.ratelimit.RateLimitDecision.allow(),false,"test");
        var response=new TransactionTemplate(transactions).execute(t->disabled.read(owner));
        assertThat(response.enabled()).isFalse(); assertThat(response.skills()).isEmpty(); assertThat(auditCount()).isEqualTo(1);
        new TransactionTemplate(transactions).executeWithoutResult(t->{masteryStore.lock(owner); masteryPrivacy.deleteUserData(owner,TEST_AT);});
        assertThat(auditCount()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from attempt_mastery_context where user_id=?",Integer.class,owner)).isZero();
    }
    @Test void v038PreservesLegacyRowsWithoutInventingIndependentHistory() {
        jdbc.update("""
            insert into learner_skill_mastery(user_id,skill_id,mastery_version,score,half_life_days,evidence_count,confidence,last_assessed_at)
            values(?,'attempt-skill-v1:LEGACY','bkt-lite-v1',.7,3,2,.3,now())
            """,owner);
        mastery.read(owner);
        assertThat(jdbc.queryForObject("select independent_evidence_count from learner_skill_mastery where user_id=?",Integer.class,owner)).isZero();
        assertThat(jdbc.queryForObject("select mastery_version from learner_skill_mastery where user_id=?",String.class,owner)).isEqualTo("bkt-lite-v1");
    }

    void deliveredHint() {
        UUID session=UUID.randomUUID();
        jdbc.update("""
            insert into tutor_sessions(id,user_id,problem_session_id,canonical_id,canonical_revision,parse_id,parse_revision,
                primary_skill_id,context_schema_version,mastery,safety_policy_version,schema_version,status,create_key,created_at,updated_at)
            values(?,?,?,?,?,?,?,?,'tutor-context-v1','NOT_AVAILABLE','learner-persistence-v1','tutor-session-v1','ACTIVE',?,now(),now())
            """,session,owner,problem,source.canonicalId(),source.canonicalRevision(),source.parseId(),source.parseRevision(),SKILL,session.toString());
        jdbc.update("""
            insert into tutor_hints(id,user_id,tutor_session_id,idempotency_key,schema_version,policy_version,cost_policy_version,
                sequence_number,level,assistance,content,language,previous_phase,phase,unproductive_count,turn_count,mastery_evidence,
                inference,commercial,reference_status,uncertain,created_at)
            values(?,?,?,?,'tutor-hint-v1','progressive-hints-v1','hint-assistance-v1',1,'H1','CONCEPTUAL','Synthetic hint','en',
                'CLARIFY_GOAL','OFFER_MINIMAL_SUPPORT',0,1,'ASSISTANCE_ONLY','DETERMINISTIC_NO_INFERENCE','NO_DEBIT','UNAVAILABLE',true,now())
            """,UUID.randomUUID(),owner,session,session.toString());
    }
    @Test void deliveredHintSnapshotIsAssistedAndLaterHintDoesNotRewriteAcceptedIndependence() {
        tagSource(); var independent=work(null); deliveredHint(); var assisted=work(null);
        classify(independent,this::proven); classify(assisted,this::proven);
        var view=mastery.read(owner);
        assertThat(view.outcomes()).anySatisfy(o->{assertThat(o.attemptId()).isEqualTo(independent.attemptId()); assertThat(o.evidenceType()).isEqualTo(LearningEvidenceType.INDEPENDENT);});
        assertThat(view.outcomes()).anySatisfy(o->{assertThat(o.attemptId()).isEqualTo(assisted.attemptId()); assertThat(o.evidenceType()).isEqualTo(LearningEvidenceType.HINT_ASSISTED);});
        assertThat(view.skills().getFirst().independentEvidenceCount()).isEqualTo(1);
        assertThat(view.skills().getFirst().assistedEvidenceCount()).isEqualTo(1);
    }
    UUID completedTwin(boolean assisted,boolean verifiedParent) {
        return completedTwin(assisted,verifiedParent,TEST_AT);
    }
    UUID completedTwin(boolean assisted,boolean verifiedParent,Instant completedAt) {
        var at=completedAt.minusSeconds(1);
        UUID id=UUID.randomUUID(),drill=UUID.randomUUID();
        new TransactionTemplate(transactions).executeWithoutResult(t->{
            sources.lockOwner(owner,problem);
            var attempt=new StudentAttempt("student-attempt-v1",id,problem,source,AttemptSourceType.MANUAL,"manual-canonical-v1",
                AttemptStatus.SUBMITTED,List.of(new StudentAttemptStep(1,"x=5",null)),null,at,at,false);
            attempts.insert(owner,id.toString(),"0".repeat(64),attempt,null);
            if(verifiedParent) attempts.publish(owner,id,wrong(id,1,"{\"domainBasis\":\"REVIEWED_REAL_TASK\"}"),at);
            var options=List.of("2-x","-x-2","x+2","x-2").stream().map(text->codec.parse(parser.normalizeV2(text,Set.of("x")))).toList();
            var artifact=new AttemptLearningEvidence.Twin("verified-twin-v1",MistakeCode.ALG_SIGN_FLIP_DISTRIBUTE,
                codec.parse(parser.normalizeV2("-(x-2)",Set.of("x"))),options,0,"attempt.mistake.ALG_SIGN_FLIP_DISTRIBUTE","x in R","FOUNDATION");
            learning.insertDrill(owner,id,drill,artifact,at);
            learning.complete(owner,learning.drill(owner,id).orElseThrow(),0,"twin-answer-"+drill,assisted,at.plusSeconds(1));
        });
        return drill;
    }
    @Autowired com.verifiedai.attempt.infrastructure.persistence.AttemptLearningRepository learning;
    @Test void verifiedTwinIsStrongerAndOriginalEventOrReplayCannotDoubleCount() {
        tagSource(); var id=completedTwin(false,true);
        // Fixed read clock after the synthetic completion; never infer future observations as current evidence.
        var service=new MasteryApplicationService(masteryStore,masterySource,Clock.offset(TEST_CLOCK,Duration.ofSeconds(2)),metrics,
            (p,k)->RateLimitDecision.allow(),true,"test");
        var view=new TransactionTemplate(transactions).execute(t->service.read(owner));
        assertThat(view.skills().getFirst().posteriorMastery()).isEqualTo(.74666667);
        assertThat(view.skills().getFirst().independentEvidenceCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from learner_mistake_events where user_id=?",Integer.class,owner)).isEqualTo(2);
        new TransactionTemplate(transactions).execute(t->service.read(owner));
        assertThat(jdbc.queryForObject("select count(*) from learner_skill_mastery_transitions where evidence_id=?",Integer.class,id)).isEqualTo(1);
    }
    @Test void delayedTargetedCheckSurvivesReplayAndDisputeRetractsOnlyEffectiveResolution() throws Exception {
        tagSource();
        var errorsAt = Clock.offset(TEST_CLOCK, Duration.ofDays(-10));
        var prerequisite = classify(work(null,"x=5",errorsAt),this::wrong);
        classify(work(null,"x=5",errorsAt),this::wrong);
        var due = TEST_AT.minusSeconds(1);
        var pendingAt = due.minus(Duration.ofDays(7));
        completedTwin(false,true,pendingAt.minusSeconds(1));
        completedTwin(false,true,pendingAt);
        completedTwin(true,true,due);
        var skill = mastery.read(owner).skills().getFirst();
        assertThat(skill.reassessmentDue()).isTrue();
        assertThat(skill.reassessmentReason()).isEqualTo(SkillReassessmentTriggerService.Reason.DELAYED_TRAP_CHECK);
        assertThat(skill.confidence()).isEqualTo(skill.evidenceConfidence());
        assertThat(skill.retentionScore()).isEqualTo(skill.retention());
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> {
            completedTwin(false,true,TEST_AT);
            assertThat(dnaStore.profiles(owner).getFirst().recurrenceState()).isEqualTo(MistakeRecurrenceState.STABLE_RESOLVED);
            status.setRollbackOnly();
        });
        assertThat(dnaStore.profiles(owner).getFirst().recurrenceState()).isEqualTo(MistakeRecurrenceState.RESOLVED_PENDING_RETENTION);
        var check = completedTwin(false,true,TEST_AT);
        var snapshot = dna.read(owner,true);
        assertThat(snapshot.profiles().getFirst().recurrenceState()).isEqualTo(MistakeRecurrenceState.STABLE_RESOLVED);
        var rows = jdbc.queryForList("select * from learner_mistake_retention_transitions where user_id=? order by event_at", owner);
        assertThat(rows).hasSize(2);
        assertThat(rows.getLast()).containsEntry("check_drill",check).containsEntry("training_eligible",false);
        jdbc.update("delete from learner_mistake_dna_profiles where user_id=?",owner);
        assertThat(dna.read(owner,true)).isEqualTo(snapshot);
        assertThat(dnaStore.profiles(owner)).isEmpty();
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Void> rebuild = () -> { tx.executeWithoutResult(t -> dna.refresh(owner)); return null; };
            var a = pool.submit(rebuild);
            var b = pool.submit(rebuild);
            a.get(15,TimeUnit.SECONDS);
            b.get(15,TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertThat(dnaStore.profiles(owner)).isEqualTo(snapshot.profiles());
        assertThat(jdbc.queryForList("select * from learner_mistake_retention_transitions where user_id=? order by event_at",owner)).isEqualTo(rows);
        var other = new SolverSourceFixture(jdbc).insertUser();
        assertThat((List<?>)dnaStore.export(other).get("retentionTransitions")).isEmpty();
        assertThatThrownBy(() -> jdbc.update("update learner_mistake_retention_transitions set user_id=? where check_drill=?",other,check))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        dna.dispute(owner,prerequisite.id(),"retention-dispute",disputeInput(prerequisite));
        assertThat(dna.read(owner,true).profiles().getFirst().recurrenceState()).isEqualTo(MistakeRecurrenceState.OBSERVED_ONCE);
        assertThat((List<?>)dnaStore.export(owner).get("retentionTransitions")).hasSize(2);
        privacy.requestDeletion(owner); privacy.confirmDeletion(owner,"DELETE");
        assertThat((List<?>)dnaStore.export(owner).get("retentionTransitions")).isEmpty();
    }

    @Test void temporalReadsDoNotRepublishOrMoveProjectionTime() {
        tagSource();
        classify(work(null),this::proven);
        var originalRepresentation = mastery.read(owner,1);
        assertThat(originalRepresentation.skills().getFirst().retentionScore()).isNull();
        assertThat(originalRepresentation.outcomes().getFirst().canonicalSkillCode()).isNull();
        var persisted = jdbc.queryForMap("select * from learner_skill_mastery where user_id=?",owner);
        var later = new MasteryApplicationService(masteryStore,masterySource,Clock.offset(TEST_CLOCK,Duration.ofDays(3)),
            metrics,(policy,key)->RateLimitDecision.allow(),true,"test",true,dna);
        var tx = new TransactionTemplate(transactions);
        var first = tx.execute(t->later.read(owner));
        assertThat(tx.execute(t->later.read(owner)).skills()).isEqualTo(first.skills());
        assertThat(first.skills().getFirst().reassessmentDue()).isTrue();
        assertThat(jdbc.queryForMap("select * from learner_skill_mastery where user_id=?",owner)).isEqualTo(persisted);
        assertThat(auditCount()).isEqualTo(1);
    }

    @Test void assistedTwinCannotUseIndependentTransitionAndUnverifiedTwinIsUnsupported() {
        tagSource(); deliveredHint(); completedTwin(true,true); completedTwin(true,false);
        var service=new MasteryApplicationService(masteryStore,masterySource,Clock.offset(TEST_CLOCK,Duration.ofSeconds(2)),metrics,
            (p,k)->RateLimitDecision.allow(),true,"test");
        var view=new TransactionTemplate(transactions).execute(t->service.read(owner));
        assertThat(view.skills().getFirst().posteriorMastery()).isEqualTo(.52171429);
        assertThat(view.skills().getFirst().independentEvidenceCount()).isZero();
        assertThat(view.skills().getFirst().assistedEvidenceCount()).isEqualTo(1);
    }
    @Test void delayedAcceptanceRebuildsInObservationOrderAndDifferentSkillsStayIsolated() {
        tagSource(); var earlier=work(null); var later=work(null);
        classify(later,this::proven); classify(earlier,this::wrong);
        var view=mastery.read(owner);
        var expected=new EvidenceStratifiedMasteryPolicy().project(
            new TransactionTemplate(transactions).execute(t->masterySource.evidence(owner)),view.asOf());
        assertThat(view.skills().getFirst().posteriorMastery()).isEqualTo(expected.state().score());
        // Pure deterministic projection per skill; no graph propagation or copied success.
        var foreignSkill=new MasteryEvidenceSource.Evidence(UUID.randomUUID(),earlier.attemptId(),null,null,UUID.randomUUID(),
            "MATH.ARITHMETIC.FRACTIONS",Instant.parse("2026-01-01T00:00:00Z"),LearningEvidenceType.INDEPENDENT,true,false,"synthetic");
        MasteryEvidenceSource mixed=id->{var values=new ArrayList<>(masterySource.evidence(id)); values.add(foreignSkill); return values;};
        var service=new MasteryApplicationService(masteryStore,mixed,TEST_CLOCK,metrics,(p,k)->RateLimitDecision.allow(),true,"test");
        var combined=new TransactionTemplate(transactions).execute(t->service.read(owner));
        assertThat(combined.skills()).hasSize(2);
        assertThat(combined.skills()).anySatisfy(skill->{assertThat(skill.canonicalSkillCode()).isEqualTo(SKILL); assertThat(skill.posteriorMastery()).isEqualTo(expected.state().score());});
    }

    @Test void accountLifecycleReachesMasteryAndItsSourceContexts() {
        tagSource();
        jdbc.update("insert into solution_learning_disclosures(user_id,problem_session_id,first_delivered_at,tracking_complete) values(?,?,?,true)",
            owner,problem,java.sql.Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
        var export=privacy.requestExport(owner);
        assertThat(privacy.downloadExport(owner,export.exportId())).containsKey("mastery");
        classify(work(null),this::proven);
        var masteryExport=masteryPrivacy.exportUserData(owner);
        assertThat(masteryExport.get("trainingEligible")).isEqualTo(false);
        for(String key:List.of("projections","transitions","contexts","disclosures"))
            assertThat((List<?>)masteryExport.get(key)).hasSize(1);
        privacy.requestDeletion(owner); privacy.confirmDeletion(owner,"DELETE");
        for(String table:List.of("learner_skill_mastery","learner_skill_mastery_transitions","attempt_mastery_context","solution_learning_disclosures"))
            assertThat(jdbc.queryForObject("select count(*) from "+table+" where user_id=?",Integer.class,owner)).isZero();
    }

    @Test void ocrDisputeAndCorrectionNeverLeaveAuthoritativeStaleMastery() {
        tagSource(); UUID line=UUID.randomUUID();
        var original=capture().submit(owner,problem,"mastery-ocr-original",new AttemptWorkInput("attempt-structured-work-request-v1",source,
            AttemptWorkInput.Engine.NATIVE_VISION,UUID.randomUUID(),null,null,null,List.of(new AttemptWorkInput.Observation(line,"x=5",null,null)),
            List.of(new AttemptWorkInput.Step(UUID.randomUUID(),"x=5",AttemptWorkInput.Role.FINAL_ANSWER,List.of(line),true))));
        classify(original,this::wrong);
        assertThat(mastery.read(owner).skills()).hasSize(1);
        var correction=corrections.correct(owner,problem,original.attemptId(),1,"mastery-ocr-correction",
            new AttemptStepOcrCorrectionService.Input("attempt-ocr-correction-request-v1",source,1,"x=4"));
        assertThat(mastery.read(owner).skills()).isEmpty();
        assertThat(works.get(owner,original.attemptId())).contains(original);
        classify(correction.work(),this::proven);
        assertThat(mastery.read(owner).skills().getFirst().posteriorMastery()).isEqualTo(.72666667);
        var c=classifications.get(owner,correction.work().attemptId()).orElseThrow();
        dna.dispute(owner,c.id(),"mastery-ocr-dispute",disputeInput(c));
        assertThat(mastery.read(owner).skills()).isEmpty();
    }
}
