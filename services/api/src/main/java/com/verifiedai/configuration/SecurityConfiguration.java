package com.verifiedai.configuration;

import com.verifiedai.identity.application.AccountSessionAccessValidator;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Configuration
@EnableMethodSecurity
class SecurityConfiguration {

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter defaultConverter = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>(defaultConverter.convert(jwt));
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                for (String role : roles) {
                    authorities.add(new SimpleGrantedAuthority(role.startsWith("ROLE_") ? role : "ROLE_" + role));
                }
            }
            List<String> directAuthorities = jwt.getClaimAsStringList("authorities");
            if (directAuthorities != null) {
                for (String auth : directAuthorities) {
                    authorities.add(new SimpleGrantedAuthority(auth));
                }
            }
            return authorities;
        });
        return converter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        JwtDecoder jwtDecoder,
        AccountSessionAccessValidator accountSessionAccessValidator
    ) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptionHandling -> exceptionHandling
                .authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write("""
                        {"type":"https://errors.verified-ai-learning.example/auth-token-expired","title":"Authentication token is invalid or expired","status":401,"code":"%s","traceId":"%s","details":{"recoverable":false,"userAction":"SIGN_IN"}}""".formatted(
                        ApiErrorCode.AUTH_TOKEN_EXPIRED.name(),
                        CorrelationIds.current()
                    ));
                })
                .accessDeniedHandler((request, response, exception) -> {
                    String uri = request.getRequestURI();
                    if (uri != null && (uri.startsWith("/v1/admin/analytics/") || uri.startsWith("/api/v1/admin/analytics/"))) {
                        response.setStatus(HttpStatus.FORBIDDEN.value());
                        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                        response.getWriter().write("""
                            {"type":"https://errors.verified-ai-learning.example/analytics-admin-forbidden","title":"Administrative role is required for analytics product metrics","status":403,"code":"%s","traceId":"%s","details":{"recoverable":false,"userAction":"USE_ADMIN_ACCOUNT"}}""".formatted(
                            ApiErrorCode.ANALYTICS_ADMIN_FORBIDDEN.name(),
                            CorrelationIds.current()
                        ));
                        return;
                    }
                    if (uri != null && (
                        uri.equals("/v1/admin/feature-flags")
                            || uri.startsWith("/v1/admin/feature-flags/")
                            || uri.equals("/api/v1/admin/feature-flags")
                            || uri.startsWith("/api/v1/admin/feature-flags/")
                    )) {
                        response.setStatus(HttpStatus.FORBIDDEN.value());
                        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                        response.getWriter().write("""
                            {"type":"https://errors.verified-ai-learning.example/feature-flag-admin-forbidden","title":"Administrative role is required for feature flag and experiment governance","status":403,"code":"%s","traceId":"%s","details":{"recoverable":false,"userAction":"USE_ADMIN_ACCOUNT"}}""".formatted(
                            ApiErrorCode.FEATURE_FLAG_ADMIN_FORBIDDEN.name(),
                            CorrelationIds.current()
                        ));
                        return;
                    }
                    new org.springframework.security.web.access.AccessDeniedHandlerImpl().handle(request, response, exception);
                })
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/auth/apple",
                    "/api/v1/auth/email/sign-up",
                    "/api/v1/auth/email/sign-in",
                    "/api/v1/auth/guest",
                    "/api/v1/auth/refresh"
                ).permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/apple/app-store").permitAll()
                .requestMatchers(
                    "/api/v1/platform/health",
                    "/api/v1/platform/readiness",
                    "/actuator/health/**",
                    "/actuator/info",
                    "/actuator/prometheus"
                ).permitAll()
                .requestMatchers("/v1/admin/**", "/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/**", "/v1/**").authenticated()
                .anyRequest().denyAll()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                .decoder(jwtDecoder)
                .jwtAuthenticationConverter(jwtAuthenticationConverter())
            ))
            .addFilterAfter(new AccountSessionStateFilter(accountSessionAccessValidator), BearerTokenAuthenticationFilter.class)
            .build();
    }
}
