package com.verifiedai.identity.infrastructure.security;

import com.verifiedai.identity.domain.model.AppleIdentityVerifier;
import com.verifiedai.identity.domain.model.VerifiedAppleIdentity;
import com.verifiedai.identity.infrastructure.configuration.IdentityAuthProperties;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

@Component
public final class NimbusAppleIdentityVerifier implements AppleIdentityVerifier {
    private static final Duration ALLOWED_IAT_SKEW = Duration.ofMinutes(2);

    private final JwtDecoder decoder;
    private final IdentityAuthProperties properties;
    private final Clock clock;

    @Autowired
    public NimbusAppleIdentityVerifier(IdentityAuthProperties properties, Clock clock) {
        this(appleDecoder(properties), properties, clock);
    }

    NimbusAppleIdentityVerifier(JwtDecoder decoder, IdentityAuthProperties properties, Clock clock) {
        this.decoder = decoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public VerifiedAppleIdentity verify(String identityToken, String rawNonce) {
        if (identityToken == null || identityToken.isBlank() || rawNonce == null || rawNonce.isBlank()) {
            throw invalidIdentity();
        }

        Jwt jwt;
        try {
            jwt = decoder.decode(identityToken);
        } catch (JwtException exception) {
            throw invalidIdentity();
        }

        Instant now = clock.instant();
        if (jwt.getIssuer() == null || !properties.apple().issuer().equals(jwt.getIssuer().toString())) {
            throw invalidIdentity();
        }
        if (!jwt.getAudience().contains(properties.apple().audience())) {
            throw invalidIdentity();
        }
        if (jwt.getExpiresAt() == null || !jwt.getExpiresAt().isAfter(now)) {
            throw invalidIdentity();
        }
        if (jwt.getIssuedAt() == null || jwt.getIssuedAt().isAfter(now.plus(ALLOWED_IAT_SKEW))) {
            throw invalidIdentity();
        }

        String expectedNonce = sha256Hex(rawNonce);
        String actualNonce = jwt.getClaimAsString("nonce");
        if (!expectedNonce.equals(actualNonce)) {
            throw invalidIdentity();
        }

        return new VerifiedAppleIdentity(jwt.getSubject());
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static JwtDecoder appleDecoder(IdentityAuthProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.apple().jwkSetUri())
            .jwsAlgorithm(SignatureAlgorithm.ES256)
            .build();
        decoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
        return decoder;
    }

    private static ApiProblemException invalidIdentity() {
        return new ApiProblemException(
            HttpStatus.UNAUTHORIZED,
            ApiErrorCode.APPLE_IDENTITY_INVALID,
            "Apple identity is invalid",
            false,
            "SIGN_IN"
        );
    }
}
