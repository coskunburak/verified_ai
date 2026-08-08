package com.verifiedai.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

final class RequestBoundsFilterTest {
    @Test
    void oversizedKnownRequestBodyIsRejected() throws ServletException, IOException {
        RequestBoundsFilter filter = new RequestBoundsFilter(new SecurityMetrics(new SimpleMeterRegistry()));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        request.setContent(new byte[4096]);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("\"code\":\"REQUEST_TOO_LARGE\"");
    }

    @Test
    void oversizedHeadersAreRejectedBeforeControllerWork() throws ServletException, IOException {
        RequestBoundsFilter filter = new RequestBoundsFilter(new SecurityMetrics(new SimpleMeterRegistry()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me/account");
        request.addHeader("Authorization", "Bearer " + "x".repeat(9000));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(431);
        assertThat(response.getContentAsString()).contains("\"code\":\"REQUEST_TOO_LARGE\"");
    }
}
