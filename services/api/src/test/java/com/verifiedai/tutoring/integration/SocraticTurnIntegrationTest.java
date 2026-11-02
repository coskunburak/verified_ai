package com.verifiedai.tutoring.integration;

import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.problem.application.canonicalization.CanonicalProblemApplicationService;
import com.verifiedai.problem.application.tutoring.TutorSourceAccess;
import com.verifiedai.profile.application.tutoring.TutorLearnerContext;
import com.verifiedai.sharedkernel.error.*;
import com.verifiedai.sharedkernel.ratelimit.RateLimitDecision;
import com.verifiedai.solving.SolverSourceFixture;
import com.verifiedai.tutoring.application.*;
import com.verifiedai.tutoring.domain.*;
import com.verifiedai.tutoring.infrastructure.persistence.TutorStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@Import(TutorTestRateLimitConfiguration.class)
@TestPropertySource(properties="verifiedai.tutoring.socratic-enabled=true")
class SocraticTurnIntegrationTest extends PostgresIntegrationTestSupport {
    @Autowired TutorSessionService service;
    @Autowired TutorSessionLifecycleContributor privacy;
    @Autowired CanonicalProblemApplicationService canonical;
    @Autowired TutorSourceAccess sources;
    @Autowired TutorLearnerContext contexts;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    SolverSourceFixture fixture;
    UUID owner,problem;
    @BeforeEach void setup() {
        fixture=new SolverSourceFixture(jdbc); owner=fixture.insertUser();
        problem=fixture.insertSupportedParse(owner,SolverSourceFixture.equationParseJson("x + 1 = 2"),1);
        canonical.canonicalize(owner,problem);
    }
    TutorSession create() { return service.create(owner,problem,"create-request"); }
    TutorTurn turn(UUID id,String key) { return service.submitTurn(owner,problem,id,key,"Synthetic step",SocraticPolicy.Intent.UNKNOWN); }
    TutorSessionService local(TutorStore store,boolean enabled,int capacity) {
        var local=new TutorSessionService(sources,contexts,store,Clock.systemUTC(),(policy,key)->RateLimitDecision.allow(),new SimpleMeterRegistry(),30,capacity);
        ReflectionTestUtils.setField(local,"socraticEnabled",enabled); return local;
    }
    @Test void freshMigrationAndSeparateRolesPreserveLegacyContracts() {
        var s=create(); var first=service.append(owner,problem,s.id(),"legacy-key","Historical learner text");
        var reply=turn(s.id(),"socratic-key");
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where version='028' or version='28'",Integer.class)).isEqualTo(1);
        assertThat(service.messages(owner,problem,s.id(),0,50)).extracting(TutorMessage::role).containsOnly(TutorPolicy.Role.LEARNER);
        assertThat(service.messages(owner,problem,s.id(),0,1).getFirst()).isEqualTo(first);
        assertThat(reply.role()).isEqualTo("TUTOR"); assertThat(reply.learnerSequenceNumber()).isEqualTo(2);
        assertThat(service.read(owner,problem,s.id()).messageCount()).isEqualTo(2);
        assertThat(reply.correctness()).isEqualTo("UNKNOWN"); assertThat(reply.referenceStatus()).isEqualTo("UNAVAILABLE");
        assertThat(reply.progress().phase()).isEqualTo(SocraticPolicy.Phase.ELICIT_CONCEPT);
        assertThat(service.turns(owner,problem,s.id(),2,50)).isEmpty();
    }
    @Test void v027LearnerHistorySurvivesAnActualForwardMigration() throws Exception {
        // Isolated synthetic schema; never clean or modify the application's migrated schema.
        String schema="tutor_upgrade_"+UUID.randomUUID().toString().replace("-","");
        try(var connection=Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
            var isolated=new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true);
            var legacy=new JdbcTemplate(isolated);
            legacy.execute("create schema "+schema);
            try {
                org.flywaydb.core.Flyway.configure().dataSource(isolated).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration/platform").target("27").load().migrate();
                connection.setSchema(schema);
                var fixtures=new SolverSourceFixture(legacy);var user=fixtures.insertUser();
                var problemId=fixtures.insertSupportedParse(user,SolverSourceFixture.equationParseJson("x + 1 = 2"),1);
                var parse=legacy.queryForObject("select id from problem_parses where user_id=?",UUID.class,user);
                var canonicalId=UUID.randomUUID(); var now=java.time.Instant.parse("2026-11-01T12:00:00Z");
                legacy.update("""
                    insert into canonical_problems(id,user_id,problem_session_id,problem_parse_id,problem_parse_revision,
                        canonical_revision,schema_version,verifier_schema_version,problem_type,task_type,
                        canonical_problem_jsonb,verifier_input_jsonb,display_jsonb,created_at)
                    values (?,?,?,?,1,1,'canonical-problem-v1','verifier-input-v1','EQUATION','SOLVE_EQUATION','{}','{}','{}',?)
                    """,canonicalId,user,problemId,parse,java.sql.Timestamp.from(now));
                var session=new TutorSession(TutorPolicy.SCHEMA,UUID.randomUUID(),problemId,canonicalId,1,parse,1,
                    "NOT_AVAILABLE","tutor-context-v1",null,null,"en","NOT_AVAILABLE",TutorPolicy.SAFETY,
                    TutorPolicy.Status.ACTIVE,true,0,0,now,now,false);
                var store=new TutorStore(legacy);store.insert(user,"upgrade-create",session);
                var message=store.append(user,session,"upgrade-message","Historical synthetic learner text",now);
                var before=store.get(user,problemId,session.id()).orElseThrow();
                org.flywaydb.core.Flyway.configure().dataSource(isolated).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration/platform").load().migrate();
                connection.setSchema(schema);
                assertThat(store.get(user,problemId,session.id()).orElseThrow()).isEqualTo(before);
                assertThat(store.messages(user,session.id(),0,50)).containsExactly(message);
                assertThat(store.progress(user,session.id())).isEqualTo(SocraticPolicy.Progress.initial());
                assertThat(store.turns(user,session.id(),0,50)).isEmpty();
            } finally { connection.setSchema("public"); legacy.execute("drop schema "+schema+" cascade"); }
        }
    }
    @Test void duplicateRequestsAndRestartCommitExactlyOneLocalReply() throws Exception {
        var s=create(); var pool=Executors.newFixedThreadPool(4);
        try {
            var jobs=new ArrayList<Callable<TutorTurn>>();
            for(int i=0;i<8;i++) jobs.add(()->turn(s.id(),"duplicate-key"));
            var ids=new HashSet<UUID>();
            for(var result:pool.invokeAll(jobs)) ids.add(result.get(20,TimeUnit.SECONDS).replyId());
            assertThat(ids).hasSize(1);
        } finally { pool.shutdownNow(); }
        var original=service.turns(owner,problem,s.id(),0,50).getFirst();
        var restarted=local(new TutorStore(jdbc),true,100);
        var replay=tx.execute(status->restarted.submitTurn(owner,problem,s.id(),"duplicate-key","Synthetic step",SocraticPolicy.Intent.UNKNOWN));
        assertThat(replay).isEqualTo(original);
        assertThat(service.messages(owner,problem,s.id(),0,50)).hasSize(1);
    }
    @Test void incompatibleRetriesAndLegacyOperationKeyReuseAreRejected() {
        var s=create(); turn(s.id(),"turn-key-1");
        assertThatThrownBy(()->service.submitTurn(owner,problem,s.id(),"turn-key-1","Synthetic step",SocraticPolicy.Intent.CHALLENGE)).isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(()->service.append(owner,problem,s.id(),"turn-key-1","Synthetic step")).isInstanceOf(ApiProblemException.class);
        service.append(owner,problem,s.id(),"legacy-key","Synthetic step");
        assertThatThrownBy(()->turn(s.id(),"legacy-key")).isInstanceOf(ApiProblemException.class);
    }
    @Test void failureBetweenLearnerAndReplyRollsBackBothAndRecoveryIsBounded() {
        var s=create();
        var failing=new TutorStore(jdbc) {
            @Override public void insertTurn(UUID user,TutorTurn turn) { throw new org.springframework.dao.DataAccessResourceFailureException("Synthetic write failure"); }
        };
        var local=local(failing,true,100);
        assertThatThrownBy(()->tx.execute(status->local.submitTurn(owner,problem,s.id(),"atomic-key","Synthetic step",SocraticPolicy.Intent.UNKNOWN))).isInstanceOf(ApiProblemException.class);
        assertThat(service.read(owner,problem,s.id()).messageCount()).isZero();
        assertThat(service.turns(owner,problem,s.id(),0,50)).isEmpty();
        turn(s.id(),"atomic-key");
        assertThat(service.messages(owner,problem,s.id(),0,50)).hasSize(1);
    }
    @Test void closureAndSourceChangeRejectNewWorkButPermitHistoricalReplay() {
        var s=create(); var original=turn(s.id(),"committed-key");
        service.close(owner,problem,s.id());
        assertThat(turn(s.id(),"committed-key")).isEqualTo(original);
        assertThatThrownBy(()->turn(s.id(),"new-key-123")).isInstanceOfSatisfying(ApiProblemException.class,e->assertThat(e.code()).isEqualTo(ApiErrorCode.TUTOR_SESSION_CLOSED));
        var active=service.create(owner,problem,"another-session");
        fixture.insertSupportedParse(owner,problem,SolverSourceFixture.equationParseJson("x + 2 = 4"),2); canonical.canonicalize(owner,problem);
        assertThatThrownBy(()->turn(active.id(),"stale-key-1")).isInstanceOfSatisfying(ApiProblemException.class,e->assertThat(e.code()).isEqualTo(ApiErrorCode.TUTOR_SOURCE_STALE));
        assertThat(service.turns(owner,problem,active.id(),0,50)).isEmpty();
    }
    @Test void closeRacingWithTurnHasOnlySerializedOutcomes() throws Exception {
        var s=create();var pool=Executors.newFixedThreadPool(2);
        try {
            var start=new CountDownLatch(1);
            var reply=pool.submit(()->{ start.await(); try { return turn(s.id(),"race-key-1"); } catch(ApiProblemException e) { assertThat(e.code()).isEqualTo(ApiErrorCode.TUTOR_SESSION_CLOSED); return null; } });
            var close=pool.submit(()->{ start.await(); return service.close(owner,problem,s.id()); });
            start.countDown(); var result=reply.get(20,TimeUnit.SECONDS); close.get(20,TimeUnit.SECONDS);
            assertThat(service.turns(owner,problem,s.id(),0,50)).hasSize(result==null?0:1);
            assertThat(service.read(owner,problem,s.id()).status()).isEqualTo(TutorPolicy.Status.CLOSED);
        } finally { pool.shutdownNow(); }
    }
    @Test void sourceChangeWhileTurnWaitsForOwnershipLockRejectsStaleWork() throws Exception {
        var s=create();var pool=Executors.newFixedThreadPool(2);
        var locked=new CountDownLatch(1);var mutate=new CountDownLatch(1);
        try {
            var sourceEdit=pool.submit(()->tx.execute(status->{
                sources.lockOwner(owner,problem);locked.countDown();
                try { if(!mutate.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Synthetic synchronization timeout"); }
                catch(InterruptedException e) { Thread.currentThread().interrupt();throw new IllegalStateException(e); }
                fixture.insertSupportedParse(owner,problem,SolverSourceFixture.equationParseJson("x + 2 = 4"),2);
                canonical.canonicalize(owner,problem);return true;
            }));
            assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();
            var pending=pool.submit(()-> {
                assertThatThrownBy(()->turn(s.id(),"stale-race-key"))
                    .isInstanceOfSatisfying(ApiProblemException.class,e->assertThat(e.code()).isEqualTo(ApiErrorCode.TUTOR_SOURCE_STALE));
            });
            mutate.countDown();sourceEdit.get(20,TimeUnit.SECONDS);pending.get(20,TimeUnit.SECONDS);
            assertThat(service.messages(owner,problem,s.id(),0,50)).isEmpty();
            assertThat(service.turns(owner,problem,s.id(),0,50)).isEmpty();
        } finally { mutate.countDown();pool.shutdownNow(); }
    }
    @Test void terminalPedagogyAndTurnBudgetAreDurableAndDoNotConsumeAnotherMessage() {
        var s=create();
        for(int i=0;i<12;i++) service.submitTurn(owner,problem,s.id(),"concept-key-"+i,"Explain a concept",SocraticPolicy.Intent.EXPLAIN_CONCEPT);
        var latest=service.turns(owner,problem,s.id(),0,50).getLast();
        assertThat(latest.progress().phase()).isEqualTo(SocraticPolicy.Phase.PAUSED);
        assertThatThrownBy(()->turn(s.id(),"beyond-budget")).isInstanceOfSatisfying(ApiProblemException.class,e->assertThat(e.code()).isEqualTo(ApiErrorCode.TUTOR_PEDAGOGY_STOPPED));
        assertThat(service.read(owner,problem,s.id()).messageCount()).isEqualTo(12);
        assertThat(service.turns(owner,problem,s.id(),0,50)).hasSize(12);
        assertThat(privacy.exportUserData(owner).toString()).contains(latest.replyId().toString());
    }
    @Test void combinedCapacityReservesTwoSlotsAndPreservesLastLegacySlot() {
        var s=create();
        for(int i=0;i<99;i++) service.append(owner,problem,s.id(),"legacy-"+i,"Synthetic");
        assertThatThrownBy(()->turn(s.id(),"no-room-key")).isInstanceOfSatisfying(ApiProblemException.class,e->assertThat(e.code()).isEqualTo(ApiErrorCode.TUTOR_CAPACITY_EXCEEDED));
        service.append(owner,problem,s.id(),"last-slot","Synthetic");
        assertThat(service.read(owner,problem,s.id()).messageCount()).isEqualTo(100);
        var second=service.create(owner,problem,"two-slot-session");
        var bounded=local(new TutorStore(jdbc),true,2);
        tx.execute(status->bounded.submitTurn(owner,problem,second.id(),"two-slot-key","Synthetic",SocraticPolicy.Intent.UNKNOWN));
        assertThatThrownBy(()->tx.execute(status->bounded.append(owner,problem,second.id(),"overflow-key","Synthetic"))).isInstanceOf(ApiProblemException.class);
    }
    @Test void disabledPolicyNeverWritesOrCallsInferenceAndStillReplaysCommittedTurns() {
        var s=create();var original=turn(s.id(),"committed-key");
        var disabled=local(new TutorStore(jdbc),false,100);
        assertThatThrownBy(()->tx.execute(status->disabled.submitTurn(owner,problem,s.id(),"disabled-key","Synthetic",SocraticPolicy.Intent.UNKNOWN)))
            .isInstanceOfSatisfying(ApiProblemException.class,e->assertThat(e.code()).isEqualTo(ApiErrorCode.TUTOR_CAPABILITY_UNAVAILABLE));
        var replay=tx.execute(status->disabled.submitTurn(owner,problem,s.id(),"committed-key","Synthetic step",SocraticPolicy.Intent.UNKNOWN));
        assertThat(replay).isEqualTo(original);
        assertThat(service.messages(owner,problem,s.id(),0,50)).hasSize(1);
        // No gateway exists in this service: the only dependencies are local policy/store/source/context.
        assertThat(Arrays.stream(TutorSessionService.class.getDeclaredFields()).map(f->f.getType().getName())).noneMatch(n->n.startsWith("com.verifiedai.ai."));
    }
    @Test void ownerIsolationCompositeForeignKeysAndPrivacyReachAllTurns() {
        var s=create(); var reply=turn(s.id(),"private-key");var other=fixture.insertUser();
        assertThatThrownBy(()->service.turns(other,problem,s.id(),0,50)).isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(()->service.submitTurn(other,problem,s.id(),"foreign-key","Synthetic",SocraticPolicy.Intent.UNKNOWN)).isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(()->jdbc.update("update tutor_turns set user_id=? where id=?",other,reply.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(privacy.exportUserData(owner).toString()).contains(reply.content(),"ELICIT_CONCEPT","UNKNOWN").doesNotContain("private-key","create-request");
        privacy.deleteUserData(owner,java.time.Instant.now());
        assertThat(jdbc.queryForObject("select count(*) from tutor_turns where user_id=?",Integer.class,owner)).isZero();
    }
}
