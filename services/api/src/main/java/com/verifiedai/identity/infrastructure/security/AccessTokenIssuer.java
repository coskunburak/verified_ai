package com.verifiedai.identity.infrastructure.security;

import com.verifiedai.identity.infrastructure.configuration.IdentityAuthProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.stereotype.Component;

@Component
public final class AccessTokenIssuer {
    private final JwtEncoder jwtEncoder;
    private final IdentityAuthProperties properties;
    private final Clock clock;

    AccessTokenIssuer(JwtEncoder jwtEncoder, IdentityAuthProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedAccessToken issue(UUID userId, UUID sessionId) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessToken().ttl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(properties.accessToken().issuer())
            .subject(userId.toString())
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .claim("sid", sessionId.toString())
            .claim("typ", "access")
            .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(token, expiresAt);
    }

    public record IssuedAccessToken(String token, Instant expiresAt) {
    }
}
