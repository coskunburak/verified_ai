package com.verifiedai.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.verifiedai.sharedkernel.ratelimit.RateLimitDecision;
import com.verifiedai.sharedkernel.ratelimit.RateLimitPolicy;
import com.verifiedai.sharedkernel.ratelimit.RateLimiter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

final class RateLimitFilterTest {
    @Test
    void deniedDecisionReturns429AndRetryAfter() throws ServletException, IOException {
        RateLimitFilter filter = new RateLimitFilter(
            (policy, keyMaterial) -> RateLimitDecision.denied(42),
            new SecurityMetrics(new SimpleMeterRegistry())
        );
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/apple");
        request.setRemoteAddr("203.0.113.10");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("42");
        assertThat(response.getContentAsString()).contains("\"code\":\"RATE_LIMIT_EXCEEDED\"");
    }

    @Test
    void degradedOpenDecisionAllowsRequest() throws ServletException, IOException {
        RateLimitFilter filter = new RateLimitFilter(new DegradedOpenLimiter(), new SecurityMetrics(new SimpleMeterRegistry()));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/me/data-exports");
        request.setRemoteAddr("203.0.113.10");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    private static final class DegradedOpenLimiter implements RateLimiter {
        @Override
        public RateLimitDecision check(RateLimitPolicy policy, String keyMaterial) {
            return RateLimitDecision.degradedOpen();
        }
    }
}
