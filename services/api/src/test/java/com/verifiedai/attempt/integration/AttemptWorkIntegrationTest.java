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
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@Import(StudentAttemptIntegrationTest.StorageConfiguration.class)
class AttemptWorkIntegrationTest extends PostgresIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired CanonicalProblemApplicationService canonical;
    @Autowired SolveSourceAccess sources;
    @Autowired VerificationSourceReader reader;
    @Autowired ManualStepNormalizer parser;
    @Autowired AttemptExtractionReader extraction;
    @Autowired com.verifiedai.problem.application.recognition.ProblemRecognitionApplicationService recognition;
    @Autowired AttemptWorkNormalizer normalizer;
    @Autowired CanonicalMathCodec codec;
    @Autowired StudentAttemptRepository attempts;
    @Autowired AttemptWorkRepository works;
    @Autowired PlatformTransactionManager transactions;
    @Autowired AccountPrivacyApplicationService privacy;
    UUID owner,problem; AttemptSourceIdentity source;
    SimpleMeterRegistry metrics;
    @BeforeEach void setup() {
        var fixture=new SolverSourceFixture(jdbc); owner=fixture.insertUser();
        problem=fixture.insertSupportedParse(owner,SolverSourceFixture.equationParseJson("2*x+3=11"),1);
        canonical.canonicalize(owner,problem); metrics=new SimpleMeterRegistry();
        source=new TransactionTemplate(transactions).execute(t->{var s=sources.current(owner,problem);return new AttemptSourceIdentity(s.canonicalProblemId(),s.canonicalRevision(),s.problemParseId(),s.problemParseRevision());});
    }
    AttemptWorkService service(boolean enabled) {
        return new AttemptWorkService(sources,attempts,works,normalizer,parser,extraction,reader,codec,metrics,
            (p,k)->RateLimitDecision.allow(),Clock.systemUTC(),transactions,enabled,true,"test",false);
    }
    AttemptWorkInput input(UUID prior,String text) {
        return new AttemptWorkInput("attempt-structured-work-request-v1",source,AttemptWorkInput.Engine.MANUAL,null,null,null,prior,List.of(),
            List.of(new AttemptWorkInput.Step(UUID.randomUUID(),text,AttemptWorkInput.Role.FINAL_ANSWER,List.of(),true)));
    }
    @Test void captureIsDurableUnassessedImmutableAndExportable() {
        var saved=service(true).submit(owner,problem,"work-exact-key",input(null,"  x=4  "));
        assertThat(service(false).read(owner,problem,saved.attemptId())).isEqualTo(saved);
        var parent=attempts.get(owner,problem,saved.attemptId()).orElseThrow();
        assertThat(parent.finalAnswer()).isEqualTo("  x=4  "); assertThat(parent.steps()).isEmpty(); assertThat(parent.analysis()).isNull();
        assertThat(attempts.request(owner,parent.id())).isNull();
        assertThat(saved.steps().getFirst().originalText()).isEqualTo("  x=4  ");
        for(String sql:List.of("update attempt_structured_work set artifact='{}'::jsonb where attempt_id=?",
            "update attempt_structured_work set training_eligible=true where attempt_id=?","delete from attempt_structured_work where attempt_id=?"))
            assertThatThrownBy(()->jdbc.update(sql,saved.attemptId())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(new StudentAttemptPrivacyLifecycleContributor(attempts).exportUserData(owner).toString()).contains("structuredWork","  x=4  ");
        assertThat(jdbc.queryForObject("select count(*) from learner_mistake_events where user_id=?",Integer.class,owner)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from learner_skill_mastery where user_id=?",Integer.class,owner)).isZero();
        assertThat(metrics.getMeters().stream().flatMap(m->m.getId().getTags().stream()).map(Object::toString)).noneMatch(s->s.contains(owner.toString()) || s.contains("x=4"));
    }
    @Test void concurrentReplayRevisionConflictAndRollbackRecovery() throws Exception {
        var command=input(null,"4"); var service=service(true); var pool=Executors.newFixedThreadPool(3);
        try {
            var calls=new ArrayList<Callable<AttemptStructuredWork>>();
            for(int i=0;i<3;i++) calls.add(()->service.submit(owner,problem,"work-concurrent",command));
            var ids=new HashSet<UUID>(); for(var result:pool.invokeAll(calls)) ids.add(result.get(20,TimeUnit.SECONDS).attemptId());
            assertThat(ids).hasSize(1); UUID id=ids.iterator().next();
            assertThat(service(false).submit(owner,problem,"work-concurrent",command).attemptId()).isEqualTo(id);
            assertThatThrownBy(()->service.submit(owner,problem,"work-concurrent",input(null,"5"))).isInstanceOfSatisfying(ApiProblemException.class,e->assertThat(e.code()).isEqualTo(ApiErrorCode.IDEMPOTENCY_KEY_REUSED));
            var second=service.submit(owner,problem,"work-revision-2",input(id,"5")); assertThat(second.revision()).isEqualTo(2);
            assertThatThrownBy(()->service.submit(owner,problem,"work-revision-fork",input(id,"6"))).isInstanceOfSatisfying(ApiProblemException.class,e->assertThat(e.code()).isEqualTo(ApiErrorCode.OPTIMISTIC_CONFLICT));
            assertThat(service.read(owner,problem,id).steps().getFirst().originalText()).isEqualTo("4");
            assertThatThrownBy(()->service(false).submit(owner,problem,"work-disabled-new",input(null,"4"))).isInstanceOf(ApiProblemException.class);
        } finally { pool.shutdownNow(); }
    }
    @Test void staleSourceBolaAndDeletedAccountCannotPublish() {
        var command=input(null,"4"); var service=service(true); var saved=service.submit(owner,problem,"work-before-stale",command);
        var foreign=new SolverSourceFixture(jdbc).insertUser();
        assertThatThrownBy(()->service.read(foreign,problem,saved.attemptId())).isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(()->service.submit(foreign,problem,"work-foreign-key",command)).isInstanceOf(ApiProblemException.class);
        source=new AttemptSourceIdentity(source.canonicalId(),source.canonicalRevision()+1,source.parseId(),source.parseRevision());
        assertThatThrownBy(()->service.submit(owner,problem,"work-stale-source",input(null,"4"))).isInstanceOfSatisfying(ApiProblemException.class,e->assertThat(e.code()).isEqualTo(ApiErrorCode.ATTEMPT_STALE_SOURCE));
        jdbc.update("update problem_sessions set current_parse_id=null where id=?",problem);
        assertThat(service.submit(owner,problem,"work-before-stale",command)).isEqualTo(saved);
        assertThat(privacy.requestDeletion(owner).status()).isEqualTo("DELETION_REQUESTED");
        assertThat(privacy.confirmDeletion(owner,"DELETE").status()).isEqualTo("DELETED");
        assertThat(works.export(owner)).isEmpty();
        assertThatThrownBy(()->service.submit(owner,problem,"work-before-stale",command)).isInstanceOfSatisfying(ApiProblemException.class,e->assertThat(e.code()).isEqualTo(ApiErrorCode.ACCOUNT_NOT_ACTIVE));
    }
    @Test void nativeCorrectionsPreserveRawAndRevisionsDeleteTogether() {
        UUID line=UUID.randomUUID();
        var command=new AttemptWorkInput("attempt-structured-work-request-v1",source,AttemptWorkInput.Engine.NATIVE_VISION,UUID.randomUUID(),null,null,null,
            List.of(new AttemptWorkInput.Observation(line,"x=O",new java.math.BigDecimal("0.2"),null)),
            List.of(new AttemptWorkInput.Step(UUID.randomUUID(),"x=0",AttemptWorkInput.Role.INTERMEDIATE,List.of(line),true)));
        var first=service(true).submit(owner,problem,"native-work-key",command);
        assertThat(first.observations().getFirst().rawText()).isEqualTo("x=O");
        assertThat(first.provenance()).isEqualTo("CLIENT_ATTESTED"); assertThat(first.provider()).isNull();
        var revised=new AttemptWorkInput(command.schemaVersion(),source,command.engine(),command.captureId(),null,null,first.attemptId(),command.observations(),
            List.of(new AttemptWorkInput.Step(UUID.randomUUID(),"x=1",AttemptWorkInput.Role.INTERMEDIATE,List.of(line),false)));
        var second=service(true).submit(owner,problem,"native-work-revised",revised);
        assertThat(second.steps().getFirst().normalizationStatus()).isEqualTo(AttemptStructuredWork.NormalizationStatus.REVIEW_REQUIRED);
        var forged=new AttemptWorkInput(command.schemaVersion(),source,command.engine(),command.captureId(),null,null,second.attemptId(),
            List.of(new AttemptWorkInput.Observation(line,"forged raw",new java.math.BigDecimal("0.2"),null)),revised.steps());
        assertThatThrownBy(()->service(true).submit(owner,problem,"native-work-forged",forged))
            .isInstanceOfSatisfying(ApiProblemException.class,e->assertThat(e.code()).isEqualTo(ApiErrorCode.REQUEST_VALIDATION_FAILED));
        new TransactionTemplate(transactions).executeWithoutResult(t->{sources.lockOwner(owner,problem); attempts.delete(owner);});
        assertThat(works.export(owner)).isEmpty();
    }
    @Test void foreignGatewayAssetCannotBeUsedAndLeavesNoPartialAttempt() {
        UUID line=UUID.randomUUID();
        var command=new AttemptWorkInput("attempt-structured-work-request-v1",source,AttemptWorkInput.Engine.GATEWAY_RECOGNITION,null,UUID.randomUUID(),UUID.randomUUID(),null,
            List.of(new AttemptWorkInput.Observation(line,"x=4",null,null)),List.of(new AttemptWorkInput.Step(UUID.randomUUID(),"x=4",AttemptWorkInput.Role.INTERMEDIATE,List.of(line),true)));
        var gateway=new AttemptWorkService(sources,attempts,works,normalizer,parser,new AttemptExtractionReader(recognition,true,metrics),reader,codec,metrics,
            (p,k)->RateLimitDecision.allow(),Clock.systemUTC(),transactions,true,true,"test",false);
        assertThatThrownBy(()->gateway.submit(owner,problem,"gateway-foreign-key",command)).isInstanceOfSatisfying(ApiProblemException.class,e->assertThat(e.code()).isEqualTo(ApiErrorCode.RESOURCE_FORBIDDEN));
        assertThat(attempts.history(owner,problem)).isEmpty(); assertThat(works.export(owner)).isEmpty();
    }
    @Test void upgradeFromV033PreservesHistoricalEvidenceWithoutBackfill() throws Exception {
        var legacy=new StudentAttemptOrchestrator(sources,reader,parser,codec,attempts,(id,count,request)->{throw new AssertionError("No verifier call");},transactions,Clock.systemUTC(),metrics,(p,k)->RateLimitDecision.allow(),true,"test");
        var saved=legacy.submitAnswer(owner,problem,"before-v034",source,List.of()," 4 ");
        String schema="work_upgrade_"+UUID.randomUUID().toString().replace("-","");
        try {
            org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).locations("classpath:db/migration/platform").target("33").load().migrate();
            try(var connection=dataSource.getConnection()) {
                var isolated=new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true));
                String prior=connection.getSchema();
                try {
                    connection.setSchema(schema);
                    isolated.update("insert into users select * from public.users where id=?",owner);
                    isolated.update("insert into problem_sessions(id,user_id,status,input_mode,created_at,updated_at) select id,user_id,status,input_mode,created_at,updated_at from public.problem_sessions where id=?",problem);
                    for(String table:List.of("problem_assets","problem_asset_derivatives","recognition_jobs","recognition_evidence","problem_parse_jobs","problem_parses","canonical_problems"))
                        isolated.update("insert into "+table+" select * from public."+table+" where user_id=?",owner);
                    isolated.update("insert into student_attempts select * from public.student_attempts where id=?",saved.id());
                    String before=isolated.queryForObject("select to_jsonb(a)::text from student_attempts a where id=?",String.class,saved.id());
                    org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).locations("classpath:db/migration/platform").load().migrate();
                    assertThat(isolated.queryForObject("select to_jsonb(a)::text from student_attempts a where id=?",String.class,saved.id())).isEqualTo(before);
                    assertThat(new StudentAttemptRepository(isolated).get(owner,problem,saved.id())).contains(saved);
                    assertThat(new AttemptWorkRepository(isolated).export(owner)).isEmpty();
                } finally { connection.setSchema(prior); }
            }
        } finally { jdbc.execute("drop schema if exists "+schema+" cascade"); }
    }
    @Test void structuredCapacityAndRateLimitCannotGrowEvidence() {
        var service=service(true);
        for(int i=0;i<30;i++) service.submit(owner,problem,"work-capacity-"+i,input(null,"4"));
        assertThatThrownBy(()->service.submit(owner,problem,"work-capacity-over",input(null,"4")))
            .isInstanceOfSatisfying(ApiProblemException.class,e->assertThat(e.code()).isEqualTo(ApiErrorCode.ATTEMPT_CAPACITY_EXCEEDED));
        var limited=new AttemptWorkService(sources,attempts,works,normalizer,parser,extraction,reader,codec,metrics,
            (p,k)->RateLimitDecision.denied(60),Clock.systemUTC(),transactions,true,true,"test",false);
        assertThatThrownBy(()->limited.submit(owner,problem,"work-rate-limit",input(null,"4")))
            .isInstanceOfSatisfying(ApiProblemException.class,e->assertThat(e.code()).isEqualTo(ApiErrorCode.RATE_LIMIT_EXCEEDED));
        assertThat(works.export(owner)).hasSize(30);
    }
}
