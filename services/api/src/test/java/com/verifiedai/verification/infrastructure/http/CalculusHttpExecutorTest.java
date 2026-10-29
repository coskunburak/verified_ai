package com.verifiedai.verification.infrastructure.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import com.verifiedai.verification.domain.model.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class CalculusHttpExecutorTest {
    HttpServer server;
    ObjectMapper mapper=new ObjectMapper();
    @AfterEach void close() { if(server!=null) server.stop(0); }
    ObjectNode wireRequest() throws Exception {
        return (ObjectNode)mapper.readTree(Files.readString(Path.of("../../packages/test-fixtures/verification/calculus-v1/cases.json")))
            .get(0).path("request");
    }
    HttpMathematicalExecutor client(String body) throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/v1/verify/calculus",exchange->{
            assertThat(exchange.getProtocol()).isEqualTo("HTTP/1.1");
            assertThat(exchange.getRequestHeaders().getFirst("Upgrade")).isNull();
            assertThat(exchange.getRequestHeaders().getFirst("X-Internal-Token")).isEqualTo("synthetic");
            exchange.getRequestBody().readAllBytes();
            var bytes=body.getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        var client=new HttpMathematicalExecutor("http://127.0.0.1:"+server.getAddress().getPort(),"synthetic",false,"test");
        client.configureCalculus(true,true,"bounded-calculus-v1"); return client;
    }
    @Test void forgedScopePlainTextAndOversizedResponsesNeverProduceProof() throws Exception {
        var request=new CalculusWireCodec().request(mapper.writeValueAsBytes(wireRequest()));
        for (var body : java.util.List.of("Invalid HTTP request", "x".repeat(16385), "{}")) {
            var client=client(body);
            var result=client.execute(request);
            assertThat(result.outcome()).isEqualTo(MathematicalExecution.Outcome.EXECUTION_FAILED);
            assertThat(result.proof()).isNull();
            server.stop(0); server=null;
        }
        var response=wireRequest(); response.put("schemaVersion","calculus-execution-result-v1");
        response.put("methodVersion","v1").put("verifierVersion","bounded-calculus-v1")
            .put("outcome","PROVEN").put("reasonCode","EXACT_DERIVATIVE");
        response.remove(java.util.List.of("variable","interval","original","candidate","restrictions","approach","integralScope"));
        response.putArray("restrictionIds"); response.putNull("diagnostic");
        response.putObject("proof").put("descriptor","DERIVATIVE_IDENTITY").put("scope","OPEN_INTERVAL")
            .putNull("leftValue").putNull("rightValue");
        ((ObjectNode)response.path("scope")).put("identity",java.util.UUID.randomUUID().toString());
        assertThat(client(response.toString()).execute(request).reasonCode()).isEqualTo(CalculusExecution.Reason.INVALID_RESPONSE);
    }
    @Test void missingPeerReadinessAndUnsupportedEnvironmentStayUnavailable() throws Exception {
        var client=client("{}");
        assertThat(client.calculusReady()).isFalse();
        client.configureCalculus(false,true,"bounded-calculus-v1");
        var request=new CalculusWireCodec().request(mapper.writeValueAsBytes(wireRequest()));
        assertThat(client.execute(request).reasonCode()).isEqualTo(CalculusExecution.Reason.DISABLED);
        var production=new HttpMathematicalExecutor("http://127.0.0.1","synthetic",false,"production");
        assertThatIllegalArgumentException().isThrownBy(()->production.configureCalculus(true,true,"bounded-calculus-v1"));
    }
}
