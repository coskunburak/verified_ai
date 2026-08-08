package com.verifiedai.configuration;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class RequestBoundsFilter extends OncePerRequestFilter {
    private static final int MAX_AUTHORIZATION_HEADER = 8192;
    private static final int MAX_CORRELATION_HEADER = 128;
    private static final int MAX_IDEMPOTENCY_HEADER = 128;

    private final SecurityMetrics metrics;

    RequestBoundsFilter(SecurityMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        if (tooLong(request.getHeader("Authorization"), MAX_AUTHORIZATION_HEADER)
            || tooLong(request.getHeader("X-Request-Id"), MAX_CORRELATION_HEADER)
            || tooLong(request.getHeader("Idempotency-Key"), MAX_IDEMPOTENCY_HEADER)) {
            metrics.requestRejected("header_too_large");
            writeProblem(response, HttpStatus.REQUEST_HEADER_FIELDS_TOO_LARGE, "Request header is too large");
            return;
        }

        long maxBodyBytes = maxBodyBytes(request);
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxBodyBytes) {
            metrics.requestRejected("body_too_large");
            writeProblem(response, HttpStatus.PAYLOAD_TOO_LARGE, "Request body is too large");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static long maxBodyBytes(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.equals("/api/v1/auth/refresh")) {
            return 2048;
        }
        if (path.equals("/api/v1/auth/apple")) {
            return 16 * 1024;
        }
        if (path.equals("/api/v1/me/learning-profile")) {
            return 4 * 1024;
        }
        if (path.equals("/api/v1/me/billing/apple/transactions")) {
            return 32 * 1024;
        }
        if (path.equals("/api/v1/webhooks/apple/app-store")) {
            return 256 * 1024;
        }
        if (path.startsWith("/api/v1/me/deletion-request") || path.startsWith("/api/v1/me/data-exports")) {
            return 8 * 1024;
        }
        return 64 * 1024;
    }

    private static boolean tooLong(String value, int maxLength) {
        return value != null && value.length() > maxLength;
    }

    private static void writeProblem(HttpServletResponse response, HttpStatus status, String title) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
            {"type":"https://errors.verified-ai-learning.example/request-too-large","title":"%s","status":%d,"code":"%s","traceId":"%s","details":{"recoverable":true,"userAction":"RETRY"}}""".formatted(
            title,
            status.value(),
            ApiErrorCode.REQUEST_TOO_LARGE.name(),
            CorrelationIds.current()
        ));
    }
}
