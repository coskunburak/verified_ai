package com.verifiedai.sharedkernel.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Request-Id";
    private static final int MAX_CORRELATION_ID_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        } else {
            correlationId = sanitize(correlationId);
        }

        try {
            MDC.put("correlationId", correlationId);
            MDC.put("traceId", correlationId);
            response.setHeader(HEADER, correlationId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("correlationId");
            MDC.remove("traceId");
        }
    }

    private static String sanitize(String value) {
        StringBuilder sanitized = new StringBuilder();
        for (int index = 0; index < value.length() && sanitized.length() < MAX_CORRELATION_ID_LENGTH; index++) {
            char character = value.charAt(index);
            if ((character >= 'a' && character <= 'z')
                || (character >= 'A' && character <= 'Z')
                || (character >= '0' && character <= '9')
                || character == '-'
                || character == '_'
                || character == '.') {
                sanitized.append(character);
            }
        }
        return sanitized.isEmpty() ? UUID.randomUUID().toString() : sanitized.toString();
    }
}
