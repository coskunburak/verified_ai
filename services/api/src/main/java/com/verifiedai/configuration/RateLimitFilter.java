package com.verifiedai.configuration;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import com.verifiedai.sharedkernel.ratelimit.RateLimitDecision;
import com.verifiedai.sharedkernel.ratelimit.RateLimitPolicy;
import com.verifiedai.sharedkernel.ratelimit.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class RateLimitFilter extends OncePerRequestFilter {
    private static final RateLimitPolicy AUTH_APPLE = new RateLimitPolicy("auth_apple", 10, Duration.ofMinutes(1), true);
    private static final RateLimitPolicy AUTH_REFRESH = new RateLimitPolicy("auth_refresh", 20, Duration.ofMinutes(1), true);
    private static final RateLimitPolicy AUTH_LOGOUT = new RateLimitPolicy("auth_logout", 30, Duration.ofMinutes(1), false);
    private static final RateLimitPolicy PURCHASE_EVIDENCE = new RateLimitPolicy("billing_purchase_evidence", 20, Duration.ofMinutes(5), false);
    private static final RateLimitPolicy DATA_EXPORT = new RateLimitPolicy("privacy_data_export", 3, Duration.ofHours(1), false);
    private static final RateLimitPolicy ACCOUNT_DELETION = new RateLimitPolicy("privacy_account_deletion", 5, Duration.ofHours(1), true);
    private static final RateLimitPolicy APPLE_WEBHOOK = new RateLimitPolicy("apple_webhook", 120, Duration.ofMinutes(1), false);

    private final RateLimiter rateLimiter;
    private final SecurityMetrics metrics;

    RateLimitFilter(RateLimiter rateLimiter, SecurityMetrics metrics) {
        this.rateLimiter = rateLimiter;
        this.metrics = metrics;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        Optional<RateLimitPolicy> policy = policyFor(request);
        if (policy.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitPolicy selectedPolicy = policy.get();
        RateLimitDecision decision = rateLimiter.check(selectedPolicy, keyMaterial(request, selectedPolicy));
        if (decision.allowed()) {
            if (decision.degraded()) {
                metrics.rateLimitDegradedOpen(selectedPolicy.name());
            }
            filterChain.doFilter(request, response);
            return;
        }

        metrics.rateLimitDenied(selectedPolicy.name());
        response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
        writeProblem(response, HttpStatus.TOO_MANY_REQUESTS, ApiErrorCode.RATE_LIMIT_EXCEEDED, "Too many requests", "RETRY");
    }

    private static Optional<RateLimitPolicy> policyFor(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("POST".equals(method) && "/api/v1/auth/apple".equals(path)) {
            return Optional.of(AUTH_APPLE);
        }
        if ("POST".equals(method) && "/api/v1/auth/refresh".equals(path)) {
            return Optional.of(AUTH_REFRESH);
        }
        if ("POST".equals(method) && "/api/v1/auth/logout".equals(path)) {
            return Optional.of(AUTH_LOGOUT);
        }
        if ("POST".equals(method) && "/api/v1/me/billing/apple/transactions".equals(path)) {
            return Optional.of(PURCHASE_EVIDENCE);
        }
        if ("POST".equals(method) && "/api/v1/me/data-exports".equals(path)) {
            return Optional.of(DATA_EXPORT);
        }
        if ("POST".equals(method) && path.startsWith("/api/v1/me/deletion-request")) {
            return Optional.of(ACCOUNT_DELETION);
        }
        if ("POST".equals(method) && "/api/v1/webhooks/apple/app-store".equals(path)) {
            return Optional.of(APPLE_WEBHOOK);
        }
        return Optional.empty();
    }

    private static String keyMaterial(HttpServletRequest request, RateLimitPolicy policy) {
        return policy.name() + ":" + clientIp(request);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private static void writeProblem(
        HttpServletResponse response,
        HttpStatus status,
        ApiErrorCode code,
        String title,
        String userAction
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
            {"type":"https://errors.verified-ai-learning.example/%s","title":"%s","status":%d,"code":"%s","traceId":"%s","details":{"recoverable":true,"userAction":"%s"}}""".formatted(
            code.name().toLowerCase().replace('_', '-'),
            title,
            status.value(),
            code.name(),
            CorrelationIds.current(),
            userAction
        ));
    }
}
