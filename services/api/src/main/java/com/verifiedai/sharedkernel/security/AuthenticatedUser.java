package com.verifiedai.sharedkernel.security;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

public record AuthenticatedUser(UUID userId, UUID sessionId) {
    public static AuthenticatedUser from(Jwt jwt) {
        try {
            return new AuthenticatedUser(UUID.fromString(jwt.getSubject()), UUID.fromString(jwt.getClaimAsString("sid")));
        } catch (RuntimeException exception) {
            throw new ApiProblemException(
                HttpStatus.UNAUTHORIZED,
                ApiErrorCode.AUTH_TOKEN_EXPIRED,
                "Authentication token is invalid",
                false,
                "SIGN_IN"
            );
        }
    }
}
