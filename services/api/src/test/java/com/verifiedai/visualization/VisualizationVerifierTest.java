package com.verifiedai.visualization;
import com.sun.net.httpserver.HttpServer;
import com.verifiedai.visualization.infrastructure.HttpVisualizationVerifier;
import com.verifiedai.visualization.application.VisualizationVerifier;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class VisualizationVerifierTest {
    @Test void internalAuthCorrelationAndResponseLimit() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var id=UUID.randomUUID(); var oversized=new java.util.concurrent.atomic.AtomicBoolean(false);
        server.createContext("/internal/v1/verify/visualization",exchange->{
            assertThat(exchange.getRequestHeaders().getFirst("X-Internal-Token")).isEqualTo("local_dev_visualization");
            assertThat(exchange.getRequestHeaders().getFirst("X-Request-Id")).isEqualTo(id.toString());
            exchange.getRequestBody().readAllBytes();
            byte[] body=(oversized.get()?"x".repeat(16385):"{}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });
        server.start();
        try {
            var client=new HttpVisualizationVerifier("http://127.0.0.1:"+server.getAddress().getPort(),"local_dev_visualization");
            assertThat(client.execute(id,"{}")).isEqualTo("{}");
            oversized.set(true);
            assertThatThrownBy(()->client.execute(id,"{}")).isInstanceOf(VisualizationVerifier.Failure.class);
            assertThatThrownBy(()->client.execute(id,"x".repeat(65537))).isInstanceOf(VisualizationVerifier.Failure.class);
        } finally {server.stop(0);}
    }
    @Test void redirectsAndCredentialBearingEndpointsAreNeverConfigured() {
        assertThatThrownBy(()->new HttpVisualizationVerifier("http://user@localhost/","local_dev_visualization")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new HttpVisualizationVerifier("file:///tmp/x","local_dev_visualization")).isInstanceOf(IllegalArgumentException.class);
    }
}
