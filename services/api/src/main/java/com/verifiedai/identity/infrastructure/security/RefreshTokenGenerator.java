package com.verifiedai.identity.infrastructure.security;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public final class RefreshTokenGenerator {
    private static final int TOKEN_BYTES = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return "rt_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
