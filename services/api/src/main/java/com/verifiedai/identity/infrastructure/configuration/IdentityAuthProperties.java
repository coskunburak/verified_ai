package com.verifiedai.identity.infrastructure.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth")
public record IdentityAuthProperties(
    Apple apple,
    AccessToken accessToken,
    RefreshToken refreshToken
) {
    public record Apple(String issuer, String audience, String jwkSetUri) {
    }

    public record AccessToken(String issuer, Duration ttl, String privateKeyPem, String publicKeyPem) {
    }

    public record RefreshToken(Duration ttl) {
    }
}
