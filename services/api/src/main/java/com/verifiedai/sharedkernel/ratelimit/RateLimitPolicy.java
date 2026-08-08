package com.verifiedai.sharedkernel.ratelimit;

import java.time.Duration;

public record RateLimitPolicy(
    String name,
    int maxRequests,
    Duration window,
    boolean failClosed
) {
}
