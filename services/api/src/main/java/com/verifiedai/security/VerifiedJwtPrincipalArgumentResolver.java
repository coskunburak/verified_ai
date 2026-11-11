package com.verifiedai.security;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class VerifiedJwtPrincipalArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return VerifiedJwtPrincipal.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof VerifiedJwtPrincipal v) {
            return v;
        }

        if (principal instanceof Jwt jwt) {
            return VerifiedJwtPrincipal.from(jwt);
        }

        if (principal instanceof org.springframework.security.core.userdetails.UserDetails user) {
            try {
                java.util.UUID uid = java.util.UUID.fromString(user.getUsername());
                return new VerifiedJwtPrincipal(uid, uid);
            } catch (Exception ignored) {
            }
        }

        return null;
    }
}
