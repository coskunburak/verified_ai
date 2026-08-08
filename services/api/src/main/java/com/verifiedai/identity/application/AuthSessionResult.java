package com.verifiedai.identity.application;

import java.time.Instant;
import java.util.UUID;

public record AuthSessionResult(
    UUID userId,
    UUID sessionId,
    String accessToken,
    Instant accessTokenExpiresAt,
    String refreshToken,
    Instant refreshTokenExpiresAt
) {
}
