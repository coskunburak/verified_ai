package com.verifiedai.visualization;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.visualization.application.*;
import com.verifiedai.problem.application.solving.SolveSourceAccess;
import com.verifiedai.problem.application.canonicalization.CanonicalProblemApplicationService;
import com.verifiedai.solving.application.canonical.CanonicalMathCodec;
import com.verifiedai.verification.application.VerificationSourceReader;
import com.verifiedai.solving.SolverSourceFixture;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.ratelimit.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;

class VisualizationIntegrationTest extends PostgresIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired SolveSourceAccess sources;
    @Autowired CanonicalProblemApplicationService canonical;
    @Autowired CanonicalMathCodec codec;
    @Autowired VerificationSourceReader reader;
    @Autowired PlatformTransactionManager transactions;
    UUID owner,problem; String query;
    @BeforeEach void setup() {
        var fixture=new SolverSourceFixture(jdbc); owner=fixture.insertUser();
        problem=fixture.insertSupportedParse(owner,SolverSourceFixture.equationParseJson("2*x + 3 = 11"),1);
        canonical.canonicalize(owner,problem);
        var q=codec.object().put("schemaVersion","math-visualization-source-v1").put("type","EQUATION").put("resolution",48);
        q.set("source",service((id,r)->"",true).context(owner,problem).path("source"));
        q.putObject("viewport").put("xMin",-10.0).put("xMax",10.0).put("yMin",-10.0).put("yMax",10.0); query=q.toString();
    }
    VisualizationService service(VisualizationVerifier verifier,boolean enabled) {
        return new VisualizationService(sources,reader,codec,verifier,transactions,(p,k)->RateLimitDecision.allow(),new SimpleMeterRegistry(),enabled);
    }
    String response(String request) {
        assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        var r=codec.object().put("schemaVersion","math-visualization-result-v1").put("policyVersion","math-visualization-policy-v1")
            .put("status","UNSUPPORTED").put("reasonCode","UNSUPPORTED_SOURCE").put("axes","X_Y_UNITLESS")
            .put("sampleSemantics","APPROXIMATE_DISPLAY_ONLY").put("trainingEligible",false);
        var input=codec.parse(request);
        for(String f:List.of("executionId","correlationId","identity","type","viewport","resolution")) r.set(f,input.path(f));
        for(String f:List.of("expression","derivativeExpression","naturalDomain","sourceDomain","solutionSet")) r.putNull(f);
        for(String f:List.of("numberLine","features","segments","concepts")) r.putArray(f);
        return r.toString();
    }
    @Test void sourceOnlyReplayHasNoDurableAttemptSolveOrBillingWrite() {
        var s=service((id,r)->response(r),true);
        var first=s.visualize(owner,problem,query); var second=s.visualize(owner,problem,query);
        assertThat(first).isEqualTo(second);
        assertThat(first.path("trainingEligible").asBoolean()).isFalse();
        for(String table:List.of("student_attempts","solve_jobs"))
            assertThat(jdbc.queryForObject("select count(*) from "+table+" where user_id=?",Integer.class,owner)).isZero();
    }
    @Test void ownerStaleDefaultOffAndInvalidViewportNeverReachWorker() {
        var calls=new java.util.concurrent.atomic.AtomicInteger(); var s=service((id,r)->{calls.incrementAndGet();return response(r);},true);
        assertThatThrownBy(()->s.visualize(UUID.randomUUID(),problem,query)).isInstanceOf(ApiProblemException.class);
        var changed=codec.parse(query).deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode)changed.path("source")).put("canonicalRevision",999);
        assertThatThrownBy(()->s.visualize(owner,problem,changed.toString())).isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(()->service((id,r)->{calls.incrementAndGet();return response(r);},false).visualize(owner,problem,query)).isInstanceOf(ApiProblemException.class);
        var bad=codec.parse(query).deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode)bad.path("viewport")).put("xMax",-10);
        assertThatThrownBy(()->s.visualize(owner,problem,bad.toString())).isInstanceOf(ApiProblemException.class);
        assertThat(calls).hasValue(0);
    }
    @Test void integerAndFloatingViewportsShareExecutionIdentity() {
        var s=service((id,r)->response(r),true);
        var first=s.visualize(owner,problem,query);
        var integer=codec.parse(query).deepCopy();
        var viewport=(com.fasterxml.jackson.databind.node.ObjectNode)integer.path("viewport");
        for(String axis:List.of("xMin","xMax","yMin","yMax")) viewport.put(axis,viewport.path(axis).asInt());
        assertThat(s.visualize(owner,problem,integer.toString())).isEqualTo(first);
    }
    @Test void midRequestRevisionRevocationCannotPublish() {
        var s=service((id,r)->{jdbc.update("update problem_sessions set current_parse_id=null where id=?",problem);return response(r);},true);
        assertThatThrownBy(()->s.visualize(owner,problem,query)).isInstanceOf(ApiProblemException.class);
    }
    @Test void accountDeletionCannotPublish() {
        var s=service((id,r)->{jdbc.update("delete from users where id=?",owner);return response(r);},true);
        assertThatThrownBy(()->s.visualize(owner,problem,query)).isInstanceOf(ApiProblemException.class);
    }
    @Test void invalidWorkerAndTimeoutAreUnavailable() {
        assertThatThrownBy(()->service((id,r)->"{}",true).visualize(owner,problem,query)).isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(()->service((id,r)->{throw new IllegalStateException("TIMEOUT");},true).visualize(owner,problem,query)).isInstanceOf(ApiProblemException.class);
    }
    @Test void simultaneousReplaysHaveSameExecution() throws Exception {
        var barrier=new CyclicBarrier(2); var s=service((id,r)->{try {barrier.await(5,TimeUnit.SECONDS);}catch(Exception ex){throw new IllegalStateException();}return response(r);},true);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->s.visualize(owner,problem,query)); var b=pool.submit(()->s.visualize(owner,problem,query));
            assertThat(a.get(10,TimeUnit.SECONDS)).isEqualTo(b.get(10,TimeUnit.SECONDS));
        }
    }
    @Test void rateLimitPrecedesExecution() {
        var s=new VisualizationService(sources,reader,codec,(id,r)->{throw new AssertionError();},transactions,
            (p,k)->RateLimitDecision.denied(60),new SimpleMeterRegistry(),true);
        assertThatThrownBy(()->s.visualize(owner,problem,query)).isInstanceOf(ApiProblemException.class);
    }
}
