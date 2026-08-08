package com.verifiedai.sharedkernel.ratelimit;

public interface RateLimiter {
    RateLimitDecision check(RateLimitPolicy policy, String keyMaterial);
}
