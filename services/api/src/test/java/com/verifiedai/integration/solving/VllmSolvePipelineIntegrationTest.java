package com.verifiedai.integration.solving;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.verifiedai.ai.VllmTestPolicy;
import com.verifiedai.ai.infrastructure.configuration.VllmSolverProperties;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.problem.application.canonicalization.CanonicalProblemApplicationService;
import com.verifiedai.problem.application.solving.SolveSource;
import com.verifiedai.solving.SolverSourceFixture;
import com.verifiedai.solving.SolverTestData;
import com.verifiedai.solving.application.*;
import com.verifiedai.solving.domain.model.SolveState;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;

/** Full real-adapter wiring against a loopback HTTP double; no actual model or GPU. */
@DirtiesContext
class VllmSolvePipelineIntegrationTest extends PostgresIntegrationTestSupport {
    static final ObjectMapper M=new ObjectMapper();
    static final AtomicInteger calls=new AtomicInteger();
    static volatile boolean missingUsage;
    static HttpServer server;
    @Autowired PrimarySolveService solves;
    @Autowired CanonicalProblemApplicationService canonical;
    @Autowired JdbcTemplate jdbc;
    UUID user,session;

    @DynamicPropertySource static void solverProperties(DynamicPropertyRegistry registry) throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/models",e->send(e,"{\"data\":[{\"id\":\""+VllmSolverProperties.MODEL+"\"}]}"));
        server.createContext("/tokenize",e->send(e,"{\"count\":100,\"max_model_len\":8192}"));
        server.createContext("/v1/chat/completions",e->{
            calls.incrementAndGet();var request=M.readTree(e.getRequestBody());
            var input=M.readTree(request.path("messages").get(1).path("content").asText());var c=input.path("canonicalProblem");
            var s=new SolveSource(UUID.fromString(input.path("canonicalProblemId").asText()),UUID.randomUUID(),UUID.randomUUID(),
                UUID.fromString(input.path("problemParseId").asText()),1,1,c.path("problemType").asText(),c.path("taskType").asText(),c.toString());
            var candidate=SolverTestData.candidate(s);candidate.put("generationOrigin","AI_MODEL");
            ((com.fasterxml.jackson.databind.node.ObjectNode)candidate.path("finalAnswer").path("right")).put("value","999");
            var output=M.createObjectNode().put("id","mock-response").put("model",VllmSolverProperties.MODEL);
            output.putArray("choices").addObject().put("finish_reason","stop").putObject("message")
                .put("role","assistant").put("content",candidate.toString());
            if(!missingUsage) output.putObject("usage").put("prompt_tokens",100).put("completion_tokens",40).put("total_tokens",140);
            send(e,output.toString());
        });
        server.start();
        Map<String,Object> p=new LinkedHashMap<>();
        p.put("app.environment","test");p.put("app.solving.enabled",true);p.put("app.solving.worker-enabled",false);
        p.put("app.solving.max-cost-micros",100);p.put("app.solving.language","tr");p.put("app.ai.usage-ledger.enabled",true);
        String v="app.ai.vllm-solver.";
        p.put(v+"enabled",true);p.put(v+"endpoint","http://127.0.0.1:"+server.getAddress().getPort()+"/v1");
        p.put(v+"allowed-origins[0]","http://127.0.0.1:"+server.getAddress().getPort());
        p.put(v+"api-key",VllmTestPolicy.CREDENTIAL);p.put(v+"evaluation-approval","synthetic-test-approval");
        p.put(v+"release.repository",VllmSolverProperties.REPOSITORY);
        for(String key:List.of("revision","tokenizer-revision","processor-revision"))p.put(v+"release."+key,VllmSolverProperties.REVISION);
        p.put(v+"release.runtime-version","0.17.1");p.put(v+"release.runtime-image-digest","sha256:"+"a".repeat(64));
        p.put(v+"release.quantization","BF16_REFERENCE");p.put(v+"release.deployment-evidence-sha256","b".repeat(64));
        p.put(v+"limits.context-tokens",8192);p.put(v+"limits.max-input-tokens",6144);p.put(v+"limits.max-output-tokens",2048);
        p.put(v+"limits.max-request-bytes",65536);p.put(v+"limits.max-response-bytes",131072);
        p.put(v+"limits.connect-timeout","1s");p.put(v+"limits.response-timeout","30s");p.put(v+"limits.max-concurrency",2);
        p.put(v+"accounting.pricing-version","synthetic-allocation-v1");
        p.put(v+"accounting.billing-semantics","PREPAID_CAPACITY_FIXED_ALLOCATION");
        p.put(v+"accounting.attempt-allocation-micros",100);p.put(v+"accounting.expected-input-micros",40);
        p.put(v+"accounting.expected-output-micros",60);p.put(v+"accounting.expected-image-micros",0);
        String r="app.ai.capabilities.SOLVE.";
        p.put(r+"enabled",true);p.put(r+"route-policy-version",VllmSolverProperties.ROUTE);p.put(r+"route-id","qwen-mock-v1");
        p.put(r+"primary.provider",VllmSolverProperties.PROVIDER);p.put(r+"primary.model",VllmSolverProperties.MODEL);
        p.put(r+"prompt-id","solver");p.put(r+"prompt-version","v002");p.put(r+"schema-version","solution-candidate-v1");
        p.put(r+"timeout","30s");p.put(r+"max-attempts",1);p.put(r+"max-response-bytes",65536);p.put(r+"max-cost-micros",100);
        p.put(r+"pricing-version","synthetic-allocation-v1");p.put(r+"cache-policy","DISABLED");p.put(r+"streaming-policy","DISABLED");
        p.put(r+"provider-retention-policy","ZERO_RETENTION");p.put(r+"privacy-region","LOCAL_TEST");
        p.put(r+"circuit-breaker-key","qwen-mock");p.put(r+"quality-gate-version",VllmSolverProperties.GATE);p.put(r+"release-stage","LOCAL_ONLY");
        String el="app.ai.routing.eligibility[0].";
        p.put(el+"capability","SOLVE");p.put(el+"target.provider",VllmSolverProperties.PROVIDER);p.put(el+"target.model",VllmSolverProperties.MODEL);
        p.put(el+"quality-gate-version",VllmSolverProperties.GATE);p.put(el+"attempt-ceiling-micros",100);p.put(el+"latency-allowance","30s");
        p.put(el+"task-types[0]","SOLVE_EQUATION");p.put(el+"difficulties[0]","UNKNOWN");p.put(el+"entitlement-tiers[0]","FREE");
        p.put(el+"privacy-region","LOCAL_TEST");p.put(el+"retention-policy","ZERO_RETENTION");p.put(el+"approved",true);
        p.put("app.ai.budget.policy-version","synthetic-budget-v1");
        for(String key:List.of("attempt-micros","request-micros","session-micros","user-daily-micros","user-monthly-micros",
            "provider-monthly-micros","global-monthly-micros","tier-request-micros.FREE"))p.put("app.ai.budget."+key,10000);
        p.forEach((key,value)->registry.add(key,()->value));
    }
    static void send(com.sun.net.httpserver.HttpExchange e,String text) throws java.io.IOException {
        byte[] data=text.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json");
        e.sendResponseHeaders(200,data.length);e.getResponseBody().write(data);e.close();
    }
    @BeforeEach void source() {
        calls.set(0);missingUsage=false;var f=new SolverSourceFixture(jdbc);user=f.insertUser();
        session=f.insertSupportedParse(user,SolverSourceFixture.equationParseJson("x + 1 = 2"),1);canonical.canonicalize(user,session);
    }
    @AfterAll static void stopServer() { if(server!=null)server.stop(0); }
    @Test void paidPolicyAndRealTransportKeepWrongMathUnverifiedWithOneDurableAttempt() {
        var command=new SolveCommand(user,session,"qwen-mock-"+session,null);var job=solves.start(command);
        assertThat(solves.start(command).jobId()).isEqualTo(job.jobId());solves.run(job.jobId());solves.run(job.jobId());
        var view=solves.get(user,session,job.jobId());assertThat(view.state()).isEqualTo(SolveState.GENERATED_UNVERIFIED);
        assertThat(view.candidate().path("finalAnswer").path("right").path("value").asText()).isEqualTo("999");
        assertThat(view.verificationNotice()).contains("has not been performed");assertThat(calls).hasValue(1);
        var row=jdbc.queryForMap("select state,reserved_micros,reported_micros,input_tokens from ai_spend_attempts where operation_id=(select operation_id from solve_jobs where id=?)",job.jobId());
        assertThat(row.get("state")).isEqualTo("REPORTED_ESTIMATE");assertThat(((Number)row.get("reserved_micros")).longValue()).isEqualTo(100);
        assertThat(((Number)row.get("input_tokens")).intValue()).isEqualTo(100);
    }
    @Test void missingUsageKeepsUnknownSpendAndCannotBeManuallyReplayed() {
        missingUsage=true;var job=solves.start(new SolveCommand(user,session,"missing-"+session,null));
        solves.run(job.jobId());solves.run(job.jobId());assertThat(calls).hasValue(1);
        var row=jdbc.queryForMap("select state,reported_micros from ai_spend_attempts where operation_id=(select operation_id from solve_jobs where id=?)",job.jobId());
        assertThat(row.get("state")).isEqualTo("UNKNOWN");assertThat(row.get("reported_micros")).isNull();
        assertThatThrownBy(()->solves.start(new SolveCommand(user,session,"retry-"+session,job.jobId())))
            .isInstanceOf(com.verifiedai.sharedkernel.error.ApiProblemException.class);
    }
}
