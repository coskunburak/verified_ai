package com.verifiedai.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.verifiedai.sharedkernel.ratelimit.RateLimitDecision;
import com.verifiedai.sharedkernel.ratelimit.RateLimitPolicy;
import com.verifiedai.sharedkernel.ratelimit.RateLimiter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

    @Test
    void uploadEndpointsUseDedicatedProblemUploadPolicies() throws ServletException, IOException {
        RecordingLimiter limiter = new RecordingLimiter();
        RateLimitFilter filter = new RateLimitFilter(limiter, new SecurityMetrics(new SimpleMeterRegistry()));

        MockHttpServletRequest presign = new MockHttpServletRequest("POST", "/api/v1/uploads/presign");
        presign.setRemoteAddr("203.0.113.10");
        filter.doFilter(presign, new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletRequest complete = new MockHttpServletRequest(
            "POST",
            "/api/v1/uploads/00000000-0000-0000-0000-000000000001/complete"
        );
        complete.setRemoteAddr("203.0.113.11");
        filter.doFilter(complete, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(limiter.policyNames()).containsExactly("problem_upload_presign", "problem_upload_complete");
    }

    private static final class DegradedOpenLimiter implements RateLimiter {
        @Override
        public RateLimitDecision check(RateLimitPolicy policy, String keyMaterial) {
            return RateLimitDecision.degradedOpen();
        }
    }

    private static final class RecordingLimiter implements RateLimiter {
        private final List<String> policyNames = new ArrayList<>();

        @Override
        public RateLimitDecision check(RateLimitPolicy policy, String keyMaterial) {
            policyNames.add(policy.name());
            return RateLimitDecision.allow();
        }

        List<String> policyNames() {
            return List.copyOf(policyNames);
        }
    }
}
