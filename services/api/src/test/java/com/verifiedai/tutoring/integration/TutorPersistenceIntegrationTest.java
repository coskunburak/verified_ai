package com.verifiedai.tutoring.integration;

import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.problem.application.canonicalization.CanonicalProblemApplicationService;
import com.verifiedai.problem.application.tutoring.TutorSourceAccess;
import com.verifiedai.profile.application.tutoring.TutorLearnerContext;
import com.verifiedai.solving.SolverSourceFixture;
import com.verifiedai.sharedkernel.error.*;
import com.verifiedai.sharedkernel.ratelimit.*;
import com.verifiedai.tutoring.application.*;
import com.verifiedai.tutoring.domain.*;
import com.verifiedai.tutoring.infrastructure.persistence.TutorStore;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@org.springframework.context.annotation.Import(TutorTestRateLimitConfiguration.class)
class TutorPersistenceIntegrationTest extends PostgresIntegrationTestSupport {
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
    @Test void migrationAndImmutableSourceSnapshotAreDurable() {
        var session=create();
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where version::integer=27 and success=true",Integer.class)).isEqualTo(1);
        assertThat(service.create(owner,problem,"create-request").id()).isEqualTo(session.id());
        assertThat(session.mastery()).isEqualTo("NOT_AVAILABLE"); assertThat(session.trainingEligible()).isFalse();
        var freshStore=new TutorStore(jdbc);
        assertThat(freshStore.get(owner,problem,session.id()).orElseThrow().canonicalId()).isEqualTo(session.canonicalId());
    }
    @Test void retryAfterServiceReinstantiationReturnsCommittedMessage() {
        var s=create();var first=service.append(owner,problem,s.id(),"message-key","Synthetic question");
        var restarted=new TutorSessionService(sources,contexts,new TutorStore(jdbc),Clock.systemUTC(),
            (policy,key)->RateLimitDecision.allow(),new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),30,100);
        var replay=tx.execute(status->restarted.append(owner,problem,s.id(),"message-key","Synthetic question"));
        assertThat(replay).isEqualTo(first);
        assertThatThrownBy(()->service.append(owner,problem,s.id(),"message-key","Different payload"))
            .isInstanceOfSatisfying(ApiProblemException.class,e->assertThat(e.code()).isEqualTo(ApiErrorCode.IDEMPOTENCY_KEY_REUSED));
    }
    @Test void concurrentDistinctAndDuplicateAppendsHaveOneTotalOrder() throws Exception {
        var s=create();var pool=Executors.newFixedThreadPool(4);
        try {
            List<Callable<TutorMessage>> calls=new ArrayList<>();
            for(int i=0;i<12;i++) { int key=i/2; calls.add(()->service.append(owner,problem,s.id(),"parallel-"+key,"Synthetic "+key)); }
            for(var future:pool.invokeAll(calls)) future.get(20,TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        var history=service.messages(owner,problem,s.id(),0,50);
        assertThat(history).hasSize(6);
        assertThat(history).extracting(TutorMessage::sequenceNumber).containsExactly(1L,2L,3L,4L,5L,6L);
        assertThat(service.messages(owner,problem,s.id(),2,2)).extracting(TutorMessage::sequenceNumber).containsExactly(3L,4L);
    }
    @Test void concurrentCreationReplaysOneSession() throws Exception {
        var pool=Executors.newFixedThreadPool(2);
        try {
            var futures=pool.invokeAll(List.<Callable<TutorSession>>of(this::create,this::create));
            assertThat(futures.get(0).get().id()).isEqualTo(futures.get(1).get().id());
        } finally { pool.shutdownNow(); }
    }
    @Test void historicalSourceSurvivesSelectionChangeAndReadOnlyReplay() {
        var s=create(); var m=service.append(owner,problem,s.id(),"old-message","Original question");
        fixture.insertSupportedParse(owner,problem,SolverSourceFixture.equationParseJson("x + 2 = 4"),2);
        canonical.canonicalize(owner,problem);
        var stale=service.read(owner,problem,s.id());
        assertThat(stale.sourceCurrent()).isFalse(); assertThat(stale.parseId()).isEqualTo(s.parseId());
        assertThat(service.append(owner,problem,s.id(),"old-message","Original question")).isEqualTo(m);
        assertThatThrownBy(()->service.append(owner,problem,s.id(),"new-message","New question"))
            .isInstanceOfSatisfying(ApiProblemException.class,e->assertThat(e.code()).isEqualTo(ApiErrorCode.TUTOR_SOURCE_STALE));
        assertThat(service.create(owner,problem,"new-source-key").parseId()).isNotEqualTo(s.parseId());
    }
    @Test void closeIsIdempotentAndCommittedRetryRemainsReadable() {
        var s=create(); var m=service.append(owner,problem,s.id(),"message-key","Question");
        var closed=service.close(owner,problem,s.id());
        assertThat(service.close(owner,problem,s.id()).version()).isEqualTo(closed.version());
        assertThat(service.append(owner,problem,s.id(),"message-key","Question")).isEqualTo(m);
        assertThatThrownBy(()->service.append(owner,problem,s.id(),"new-message","Question")).isInstanceOf(ApiProblemException.class);
    }
    @Test void foreignOwnersAndDeletingAccountsCannotReadOrWrite() {
        var s=create(); var other=fixture.insertUser();
        assertThatThrownBy(()->service.create(other,problem,"foreign-key")).isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(()->service.read(other,problem,s.id())).isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(()->service.messages(other,problem,s.id(),0,50)).isInstanceOf(ApiProblemException.class);
        jdbc.update("update users set status='DELETION_REQUESTED' where id=?",owner);
        assertThatThrownBy(()->service.append(owner,problem,s.id(),"delayed-key","Question")).isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(this::create).isInstanceOf(ApiProblemException.class);
    }
    @Test void unavailableReviewSourceDoesNotCreateAnything() {
        jdbc.update("update problem_parses set review_required=true where problem_session_id=?",problem);
        assertThatThrownBy(this::create).isInstanceOfSatisfying(ApiProblemException.class,e->assertThat(e.code()).isEqualTo(ApiErrorCode.TUTOR_SOURCE_NOT_READY));
        assertThat(jdbc.queryForObject("select count(*) from tutor_sessions where user_id=?",Integer.class,owner)).isZero();
    }
    @Test void databaseRejectsCrossOwnerAndForgedLineage() {
        var s=create();var other=fixture.insertUser();
        assertThatThrownBy(()->jdbc.update("update tutor_sessions set user_id=? where id=?",other,s.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("update tutor_sessions set parse_revision=999 where id=?",s.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        service.append(owner,problem,s.id(),"message-key","Question");
        assertThatThrownBy(()->jdbc.update("update tutor_messages set user_id=? where tutor_session_id=?",other,s.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void privacyExportContainsMessagesAndNoRetryMetadataThenCascades() {
        var s=create();service.append(owner,problem,s.id(),"private-key","Synthetic personal learning text");
        var export=privacy.exportUserData(owner).toString();
        assertThat(export).contains("Synthetic personal learning text").doesNotContain("private-key","create-request","idempotency");
        privacy.deleteUserData(owner,java.time.Instant.now());
        assertThat(jdbc.queryForObject("select count(*) from tutor_messages where user_id=?",Integer.class,owner)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from tutor_sessions where user_id=?",Integer.class,owner)).isZero();
    }
    @Test void configuredSessionAndMessageCapsPreserveCommittedRetries() {
        var bounded=new TutorSessionService(sources,contexts,new TutorStore(jdbc),Clock.systemUTC(),
            (policy,key)->RateLimitDecision.allow(),new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),1,1);
        var s=tx.execute(status->bounded.create(owner,problem,"bounded-key"));
        assertThatThrownBy(()->tx.execute(status->bounded.create(owner,problem,"another-key"))).isInstanceOf(ApiProblemException.class);
        var m=tx.execute(status->bounded.append(owner,problem,s.id(),"message-key","Question"));
        var replayed=tx.execute(status->bounded.append(owner,problem,s.id(),"message-key","Question"));
        assertThat(replayed).isEqualTo(m);
        assertThatThrownBy(()->tx.execute(status->bounded.append(owner,problem,s.id(),"another-key","Question"))).isInstanceOf(ApiProblemException.class);
    }
    @Test void sourceDeletionCascadesAndRateLimitFailsClosed() {
        var s=create();service.append(owner,problem,s.id(),"message-key","Question");
        jdbc.update("delete from problem_sessions where id=?",problem);
        assertThat(jdbc.queryForObject("select count(*) from tutor_messages where user_id=?",Integer.class,owner)).isZero();
        var limited=new TutorSessionService(sources,contexts,new TutorStore(jdbc),Clock.systemUTC(),
            (policy,key)->RateLimitDecision.denied(60),new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),30,100);
        assertThatThrownBy(()->tx.execute(status->limited.create(owner,problem,"limited-key")))
            .isInstanceOfSatisfying(ApiProblemException.class,e->assertThat(e.code()).isEqualTo(ApiErrorCode.RATE_LIMIT_EXCEEDED));
    }
}
