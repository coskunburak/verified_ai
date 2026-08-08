package com.verifiedai.sharedkernel.ratelimit;

public record RateLimitDecision(
    boolean allowed,
    boolean degraded,
    long retryAfterSeconds
) {
    public static RateLimitDecision allow() {
        return new RateLimitDecision(true, false, 0);
    }

    public static RateLimitDecision degradedOpen() {
        return new RateLimitDecision(true, true, 0);
    }

    public static RateLimitDecision denied(long retryAfterSeconds) {
        return new RateLimitDecision(false, false, Math.max(1, retryAfterSeconds));
    }
}
