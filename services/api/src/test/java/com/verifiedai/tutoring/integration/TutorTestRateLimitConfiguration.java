package com.verifiedai.tutoring.integration;

import com.verifiedai.sharedkernel.ratelimit.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;

/** Synthetic deterministic limiter; Redis behavior belongs to shared-kernel tests. */
@TestConfiguration
public class TutorTestRateLimitConfiguration {
    @Bean @Primary RateLimiter tutorTestLimiter() {
        var counts=new ConcurrentHashMap<String,AtomicInteger>();
        return (policy,key)->counts.computeIfAbsent(policy.name()+key,k->new AtomicInteger()).incrementAndGet()>policy.maxRequests()
            ? RateLimitDecision.denied(60) : RateLimitDecision.allow();
    }
}
