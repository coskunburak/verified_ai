package com.verifiedai.security;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class VerifiedSecurityWebMvcConfigurer implements WebMvcConfigurer, BeanPostProcessor {

    private final VerifiedJwtPrincipalArgumentResolver resolver;

    public VerifiedSecurityWebMvcConfigurer(VerifiedJwtPrincipalArgumentResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(0, resolver);
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof RequestMappingHandlerAdapter adapter) {
            List<HandlerMethodArgumentResolver> current = adapter.getArgumentResolvers();
            if (current != null) {
                List<HandlerMethodArgumentResolver> updated = new ArrayList<>();
                updated.add(resolver);
                updated.addAll(current);
                adapter.setArgumentResolvers(updated);
            }
        }
        return bean;
    }
}
