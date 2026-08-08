package com.verifiedai.configuration;

import com.verifiedai.identity.application.AccountSessionAccessValidationResult;
import com.verifiedai.identity.application.AccountSessionAccessValidator;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

class AccountSessionStateFilter extends OncePerRequestFilter {
    private final AccountSessionAccessValidator accessValidator;

    AccountSessionStateFilter(AccountSessionAccessValidator accessValidator) {
        this.accessValidator = accessValidator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        if (shouldSkip(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            filterChain.doFilter(request, response);
            return;
        }

        AuthenticatedUser authenticatedUser = AuthenticatedUser.from(jwt);
        AccountSessionAccessValidationResult result = accessValidator.validate(
            authenticatedUser.userId(),
            authenticatedUser.sessionId(),
            request.getRequestURI()
        );
        if (result.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        writeProblem(response, result);
    }

    private static boolean shouldSkip(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/")
            || ("/api/v1/auth/apple".equals(path) && "POST".equals(request.getMethod()))
            || ("/api/v1/auth/refresh".equals(path) && "POST".equals(request.getMethod()))
            || ("/api/v1/webhooks/apple/app-store".equals(path) && "POST".equals(request.getMethod()))
            || path.startsWith("/api/v1/platform/");
    }

    private static void writeProblem(HttpServletResponse response, AccountSessionAccessValidationResult result) throws IOException {
        response.setStatus(result.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
            {"type":"https://errors.verified-ai-learning.example/%s","title":"%s","status":%d,"code":"%s","traceId":"%s","details":{"recoverable":false,"userAction":"%s"}}""".formatted(
            result.code().name().toLowerCase().replace('_', '-'),
            result.title(),
            result.status().value(),
            result.code().name(),
            CorrelationIds.current(),
            result.userAction()
        ));
    }
}
