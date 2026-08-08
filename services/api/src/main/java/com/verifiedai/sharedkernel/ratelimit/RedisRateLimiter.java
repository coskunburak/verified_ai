package com.verifiedai.sharedkernel.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
class RedisRateLimiter implements RateLimiter {
    private final StringRedisTemplate redisTemplate;

    RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public RateLimitDecision check(RateLimitPolicy policy, String keyMaterial) {
        String key = "verified-ai:rate-limit:" + policy.name() + ":" + digest(keyMaterial);
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, policy.window());
            }
            if (count != null && count > policy.maxRequests()) {
                Long ttl = redisTemplate.getExpire(key);
                return RateLimitDecision.denied(ttl == null || ttl < 1 ? policy.window().toSeconds() : ttl);
            }
            return RateLimitDecision.allow();
        } catch (DataAccessException exception) {
            return policy.failClosed()
                ? RateLimitDecision.denied(Math.max(1, Duration.ofSeconds(5).toSeconds()))
                : RateLimitDecision.degradedOpen();
        }
    }

    private static String digest(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(messageDigest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
