package com.verifiedai.attempt.infrastructure;
import com.sun.net.httpserver.HttpServer;
import com.verifiedai.attempt.infrastructure.http.HttpAttemptVerifierClient;
import com.verifiedai.attempt.domain.*;
import java.net.InetSocketAddress;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class AttemptVerifierClientTest {
    @Test void authenticatedWireBindsIdentityAndRejectsInvalidEvidence() throws Exception {
        var id=UUID.randomUUID();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var requests=new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/internal/v1/verify/attempt",exchange->{
            assertThat(exchange.getRequestHeaders().getFirst("X-Internal-Token")).isEqualTo("local_dev_synthetic");
            assertThat(exchange.getRequestHeaders().getFirst("X-Request-Id")).isEqualTo(id.toString());
            exchange.getRequestBody().readAllBytes();
            var result=AttemptAnalysisResult.unavailable(requests.getAndIncrement()==0?id:UUID.randomUUID(),1,AttemptAnalysisResult.Reason.UNSUPPORTED_MATH);
            byte[] bytes=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(result);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);
            exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        try {
            var client=new HttpAttemptVerifierClient("http://127.0.0.1:"+server.getAddress().getPort(),"local_dev_synthetic");
            assertThat(client.verify(id,1,"{}").steps().getFirst().reasonCode()).isEqualTo(AttemptAnalysisResult.Reason.UNSUPPORTED_MATH);
            assertThat(client.verify(id,1,"{}").steps().getFirst().reasonCode()).isEqualTo(AttemptAnalysisResult.Reason.INVALID_RESPONSE);
            assertThat(client.verify(id,1,null).outcome()).isEqualTo(AttemptOutcome.NEEDS_REVIEW);
        } finally { server.stop(0); }
    }
    @Test void arbitraryEndpointsAndOversizedPayloadReject() {
        assertThatThrownBy(()->new HttpAttemptVerifierClient("http://user@host/path","local_dev_synthetic")).isInstanceOf(IllegalArgumentException.class);
        var client=new HttpAttemptVerifierClient("http://127.0.0.1:1","local_dev_synthetic");
        assertThat(client.verify(UUID.randomUUID(),1,"x".repeat(65537)).firstWrongStepIndex()).isNull();
    }
    @Test void comparisonWireRejectsForgedNestedProofAndWrongExecution() throws Exception {
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode evidence;
        try(var input=getClass().getResourceAsStream("/schemas/fixtures/method-comparison-public-v2.json")) {
            evidence=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(input).path("evidence");
        }
        var id=UUID.fromString(evidence.path("executionId").asText());
        var request=mapper.createObjectNode().put("schemaVersion","method-comparison-v2");request.set("source",evidence.path("source"));
        var body=new java.util.concurrent.atomic.AtomicReference<>(evidence.toString());
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/v1/verify/attempt-learning",exchange->{
            assertThat(exchange.getRequestHeaders().getFirst("X-Internal-Token")).isEqualTo("local_dev_synthetic");
            exchange.getRequestBody().readAllBytes();
            var bytes=body.get().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);
            exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        try {
            var client=new HttpAttemptVerifierClient("http://127.0.0.1:"+server.getAddress().getPort(),"local_dev_synthetic");
            assertThat(client.comparison(id,request.toString()).methods()).hasSize(3);
            assertThatThrownBy(()->client.comparison(UUID.randomUUID(),request.toString())).isInstanceOf(IllegalStateException.class);
            ((com.fasterxml.jackson.databind.node.ObjectNode)evidence.path("methods").get(0)).put("studentVerified",true);
            body.set(evidence.toString());
            assertThatThrownBy(()->client.comparison(id,request.toString())).isInstanceOf(IllegalStateException.class);
        } finally { server.stop(0); }
    }

}
