package com.verifiedai.ai.infrastructure.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import com.verifiedai.ai.VllmTestPolicy;
import com.verifiedai.ai.application.*;
import com.verifiedai.ai.infrastructure.configuration.VllmSolverProperties;
import com.verifiedai.solving.SolverTestData;
import com.verifiedai.solving.application.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.core.io.DefaultResourceLoader;
import static org.assertj.core.api.Assertions.*;

class VllmSolveProviderAdapterTest {
    final ObjectMapper mapper=new ObjectMapper();
    final com.verifiedai.problem.application.solving.SolveSource source=SolverTestData.source();
    HttpServer server;
    VllmSolveProviderAdapter adapter;
    ObjectNode completion;
    volatile String raw;
    volatile int status=200;
    volatile int tokenCount=100;
    volatile ObjectNode requestBody;
    final AtomicInteger calls=new AtomicInteger();
    @BeforeEach void setup() throws Exception {
        var candidate=SolverTestData.candidate(source);candidate.put("generationOrigin","AI_MODEL");
        completion=mapper.createObjectNode().put("id","chat-test").put("model",VllmSolverProperties.MODEL);
        completion.putObject("usage").put("prompt_tokens",100).put("completion_tokens",200).put("total_tokens",300);
        var choice=completion.putArray("choices").addObject().put("finish_reason","stop");
        choice.putObject("message").put("role","assistant").put("content",candidate.toString());
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/tokenize",e->{
            byte[] b=("{\"count\":"+tokenCount+",\"max_model_len\":8192}").getBytes(StandardCharsets.UTF_8);
            e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,b.length);
            e.getResponseBody().write(b);e.close();
        });
        server.createContext("/v1/models",e->{
            byte[] b=("{\"data\":[{\"id\":\""+completion.path("model").asText()+"\"}]}").getBytes(StandardCharsets.UTF_8);
            e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,b.length);
            e.getResponseBody().write(b);e.close();
        });
        server.createContext("/v1/chat/completions",e->{
            calls.incrementAndGet();requestBody=(ObjectNode)mapper.readTree(e.getRequestBody());
            byte[] b=(raw==null?completion.toString():raw).getBytes(StandardCharsets.UTF_8);
            e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(status,b.length);
            e.getResponseBody().write(b);e.close();
        });
        server.start();
        adapter=new VllmSolveProviderAdapter(VllmTestPolicy.config(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/v1")),
            Clock.systemUTC(),new DefaultResourceLoader(),PromptRegistry.defaults(),OutputSchemaRegistry.defaults(),"test");
    }
    @AfterEach void close() { if(adapter!=null) adapter.close(); if(server!=null) server.stop(0); }
    AiProviderRequest request(long budget,Instant deadline,Duration timeout) {
        return new AiProviderRequest(VllmTestPolicy.command(source,budget,deadline),VllmTestPolicy.plan(),
            VllmTestPolicy.plan().primary(),timeout,budget);
    }
    AiSolveResult execute() { return adapter.execute(request(100,Instant.now().plusSeconds(30),Duration.ofSeconds(30))); }
    void rejects(AiProviderFailureClass kind) {
        assertThatThrownBy(this::execute).isInstanceOfSatisfying(AiProviderException.class,e->{
            assertThat(e.failureClass()).isEqualTo(kind);assertThat(e.retryable()).isFalse();
            assertThat(e.getMessage()).doesNotContain(VllmTestPolicy.CREDENTIAL,"private student");
        });
    }
    @Test void validResponseMapsUsageExactProvenanceAndTurkishPublicSteps() throws Exception {
        var result=execute();
        assertThat(result.usage().inputTokens()).isEqualTo(100);assertThat(result.usage().estimatedCostMicros()).isEqualTo(100);
        assertThat(result.provenance().model()).isEqualTo(VllmSolverProperties.MODEL);
        assertThat(requestBody.path("max_tokens").asInt()).isEqualTo(2048);
        assertThat(requestBody.path("chat_template_kwargs").path("enable_thinking").asBoolean()).isFalse();
        assertThat(requestBody.path("messages").get(0).path("content").asText()).contains("language: tr");
        var sent=mapper.readTree(requestBody.path("messages").get(1).path("content").asText());
        assertThat(sent.path("canonicalProblem")).isEqualTo(mapper.readTree(source.canonicalJson()));
        assertThat(new SolutionCandidateValidator().validate(result.rawOutputJson(),source,adapter.providerId())).isNotNull();
    }
    @Test void wrongButWellFormedMathIsStillUnverifiedAndAuthorityInjectionIsRejected() throws Exception {
        var c=SolverTestData.candidate(source);c.put("generationOrigin","AI_MODEL");
        ((ObjectNode)c.path("finalAnswer").path("right")).put("value","999");
        ((ObjectNode)completion.path("choices").get(0).path("message")).put("content",c.toString());
        var result=execute();var validator=new SolutionCandidateValidator();
        assertThat(validator.serialize(validator.validate(result.rawOutputJson(),source,adapter.providerId()))).doesNotContain("VERIFIED");
        c.put("verified",true);
        assertThatThrownBy(()->validator.validate(c.toString(),source,adapter.providerId())).isInstanceOf(CandidateRejected.class);
        c.remove("verified");((ObjectNode)c.path("steps").get(0)).put("explanation","VERIFIED by model");
        assertThatThrownBy(()->validator.validate(c.toString(),source,adapter.providerId())).isInstanceOf(CandidateRejected.class);
    }
    @Test void malformedAndOversizedResponsesFailClosed() {
        raw="{";rejects(AiProviderFailureClass.SCHEMA_INVALID);
        raw="x".repeat(131073);rejects(AiProviderFailureClass.OUTPUT_TOO_LARGE);
    }
    @Test void missingUsageWrongIdentityTruncationAndMalformedCandidateAreRejected() {
        completion.remove("usage");rejects(AiProviderFailureClass.SCHEMA_INVALID);
        completion.put("model","wrong-revision");rejects(AiProviderFailureClass.POLICY_BLOCKED);
        completion.put("model",VllmSolverProperties.MODEL);
        ((ObjectNode)completion.path("choices").get(0)).put("finish_reason","length");rejects(AiProviderFailureClass.SCHEMA_INVALID);
    }
    @Test void httpErrorsAreClassifiedWithoutReplayOrLeakingBodies() {
        raw="{\"error\":\"private student "+VllmTestPolicy.CREDENTIAL+"\"}";
        status=401;rejects(AiProviderFailureClass.INVALID_AUTH);
        status=429;rejects(AiProviderFailureClass.RATE_LIMITED);
        status=503;rejects(AiProviderFailureClass.PROVIDER_UNAVAILABLE);
        status=302;rejects(AiProviderFailureClass.POLICY_BLOCKED);
        assertThat(calls).hasValue(4);
    }
    @Test void inputTokenLimitBudgetAndExpiredDeadlinePreventGeneration() {
        tokenCount=6145;rejects(AiProviderFailureClass.UNSUPPORTED_PAYLOAD);
        assertThatThrownBy(()->adapter.execute(request(99,Instant.now().plusSeconds(30),Duration.ofSeconds(30))))
            .isInstanceOfSatisfying(AiProviderException.class,e->assertThat(e.failureClass()).isEqualTo(AiProviderFailureClass.BUDGET_EXCEEDED));
        assertThatThrownBy(()->adapter.execute(request(100,Instant.now().minusSeconds(1),Duration.ofSeconds(30))))
            .isInstanceOfSatisfying(AiProviderException.class,e->assertThat(e.failureClass()).isEqualTo(AiProviderFailureClass.DEADLINE_EXCEEDED));
        assertThat(calls).hasValue(0);
    }
    @Test void fullBodyTimeoutDisconnectsWithoutReplay() throws Exception {
        server.removeContext("/v1/chat/completions");
        var release=new java.util.concurrent.CountDownLatch(1);
        server.createContext("/v1/chat/completions",e->{
            calls.incrementAndGet();e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,0);
            try { e.getResponseBody().write('{');e.getResponseBody().flush();release.await(3,java.util.concurrent.TimeUnit.SECONDS); }
            catch(InterruptedException interrupted) {Thread.currentThread().interrupt();} finally {e.close();}
        });
        try {
            assertThatThrownBy(()->adapter.execute(request(100,Instant.now().plusSeconds(2),Duration.ofMillis(500))))
                .isInstanceOfSatisfying(AiProviderException.class,e->{
                    assertThat(e.failureClass()).isIn(AiProviderFailureClass.TIMEOUT,AiProviderFailureClass.DEADLINE_EXCEEDED);
                    assertThat(e.retryable()).isFalse();
                });
            assertThat(calls.get()).isLessThanOrEqualTo(1);
        } finally { release.countDown(); }
    }
    @Test void healthRequiresExactModelIdentity() {
        completion.put("model","wrong-revision");
        assertThat(adapter.health(VllmTestPolicy.plan().primary()).state()).isEqualTo(AiProviderHealth.State.OPEN);
    }

    ConfiguredAiModelGateway gateway(AiUsageRecorder ledger,AiSpendGuard guard) {
        var planner=new AiRoutePlanner() {
            public boolean enabled(AiCapability c) {return c==AiCapability.SOLVE;}
            public AiRoutePlan routePlan(AiRouteContext c) {return VllmTestPolicy.plan();}
            public java.util.Map<AiCapability,AiRoutePolicy> policies() {
                return java.util.Map.of(AiCapability.SOLVE,AiRoutePolicy.enabled(VllmTestPolicy.plan()));
            }
        };
        return new ConfiguredAiModelGateway(planner,AiCapabilityRegistry.defaults(),java.util.List.of(adapter),ledger,
            AiGatewayMetrics.noOp(),Clock.systemUTC(),guard);
    }
    @Test void unknownUsageRetainsReservationAndIsNotReplayed() {
        var guard=org.mockito.Mockito.mock(AiSpendGuard.class);
        completion.remove("usage");
        var command=VllmTestPolicy.command(source,100,Instant.now().plusSeconds(30));
        var result=gateway(AiUsageRecorder.noOp(),guard).execute(command);
        assertThat(result.succeeded()).isFalse();assertThat(result.retryable()).isFalse();
        assertThat(result.usage().estimatedCostMicros()).isEqualTo(100);
        assertThat(result.usage().inputTokens()).isNull();assertThat(calls).hasValue(1);
        org.mockito.Mockito.verify(guard).report(command,1,null,AiProviderFailureClass.SCHEMA_INVALID);
    }
    @Test void aggregateBudgetAndDuplicateOperationBlockBeforeDispatch() {
        var guard=org.mockito.Mockito.mock(AiSpendGuard.class);
        org.mockito.Mockito.doThrow(new AiProviderException(AiProviderFailureClass.BUDGET_EXCEEDED,false,"test budget"))
            .when(guard).reserve(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyLong());
        var command=VllmTestPolicy.command(source,100,Instant.now().plusSeconds(30));
        assertThat(gateway(AiUsageRecorder.noOp(),guard).execute(command).status()).isEqualTo(AiExecutionStatus.BLOCKED_BUDGET);
        var ledger=org.mockito.Mockito.mock(AiUsageRecorder.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("duplicate operation")).when(ledger).reserve(org.mockito.ArgumentMatchers.any());
        assertThat(gateway(ledger,guard).execute(command).succeeded()).isFalse();assertThat(calls).hasValue(0);
    }
}
