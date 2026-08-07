package com.verifiedai.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.verifiedai.integration.PostgresIntegrationTestSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

final class PlatformHealthControllerTest extends PostgresIntegrationTestSupport {

    @Value("${local.server.port}")
    int port;

    @Test
    void healthIncludesCorrelationHeaderAndSafeBody() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/platform/health"))
            .GET()
            .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("X-Request-Id")).isPresent();
        assertThat(response.body()).contains("\"status\":\"UP\"");
        assertThat(response.body()).doesNotContain("password");
    }
}
