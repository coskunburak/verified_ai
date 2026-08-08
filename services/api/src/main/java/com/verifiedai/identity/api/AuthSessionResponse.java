package com.verifiedai.identity.api;

import com.verifiedai.identity.application.AuthSessionResult;
import java.time.Instant;
import java.util.UUID;

public record AuthSessionResponse(
    UUID userId,
    UUID sessionId,
    String tokenType,
    String accessToken,
    Instant accessTokenExpiresAt,
    String refreshToken,
    Instant refreshTokenExpiresAt
) {
    static AuthSessionResponse from(AuthSessionResult result) {
        return new AuthSessionResponse(
            result.userId(),
            result.sessionId(),
            "Bearer",
            result.accessToken(),
            result.accessTokenExpiresAt(),
            result.refreshToken(),
            result.refreshTokenExpiresAt()
        );
    }
}
