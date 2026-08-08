package com.verifiedai.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import com.verifiedai.identity.infrastructure.configuration.IdentityAuthProperties;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class NimbusAppleIdentityVerifierTest {
    private static final Instant NOW = Instant.parse("2026-08-08T12:00:00Z");

    private HttpServer jwksServer;

    @AfterEach
    void stopServer() {
        if (jwksServer != null) {
            jwksServer.stop(0);
        }
    }

    @Test
    void verifiesValidAppleIdentityToken() throws Exception {
        ECKey key = new ECKeyGenerator(Curve.P_256).keyID("apple-test-key").generate();
        NimbusAppleIdentityVerifier verifier = verifierFor(key);

        String token = signedToken(key, "subject-1", "com.verifiedai.learning", "nonce", NOW.plusSeconds(300));

        assertThat(verifier.verify(token, "nonce").providerSubject()).isEqualTo("subject-1");
    }

    @Test
    void rejectsWrongAudience() throws Exception {
        ECKey key = new ECKeyGenerator(Curve.P_256).keyID("apple-test-key").generate();
        NimbusAppleIdentityVerifier verifier = verifierFor(key);

        String token = signedToken(key, "subject-1", "wrong-audience", "nonce", NOW.plusSeconds(300));

        assertThatThrownBy(() -> verifier.verify(token, "nonce")).isInstanceOf(ApiProblemException.class);
    }

    @Test
    void rejectsWrongNonce() throws Exception {
        ECKey key = new ECKeyGenerator(Curve.P_256).keyID("apple-test-key").generate();
        NimbusAppleIdentityVerifier verifier = verifierFor(key);

        String token = signedToken(key, "subject-1", "com.verifiedai.learning", "nonce", NOW.plusSeconds(300));

        assertThatThrownBy(() -> verifier.verify(token, "other-nonce")).isInstanceOf(ApiProblemException.class);
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        ECKey key = new ECKeyGenerator(Curve.P_256).keyID("apple-test-key").generate();
        NimbusAppleIdentityVerifier verifier = verifierFor(key);

        String token = signedToken(key, "subject-1", "com.verifiedai.learning", "nonce", NOW.minusSeconds(10));

        assertThatThrownBy(() -> verifier.verify(token, "nonce")).isInstanceOf(ApiProblemException.class);
    }

    @Test
    void rejectsInvalidSignature() throws Exception {
        ECKey trustedKey = new ECKeyGenerator(Curve.P_256).keyID("apple-test-key").generate();
        ECKey untrustedKey = new ECKeyGenerator(Curve.P_256).keyID("untrusted-key").generate();
        NimbusAppleIdentityVerifier verifier = verifierFor(trustedKey);

        String token = signedToken(untrustedKey, "subject-1", "com.verifiedai.learning", "nonce", NOW.plusSeconds(300));

        assertThatThrownBy(() -> verifier.verify(token, "nonce")).isInstanceOf(ApiProblemException.class);
    }

    private NimbusAppleIdentityVerifier verifierFor(ECKey key) throws Exception {
        jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String jwks = "{\"keys\":[" + key.toPublicJWK().toJSONString() + "]}";
        jwksServer.createContext("/auth/keys", exchange -> {
            byte[] body = jwks.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        jwksServer.start();
        String jwksUri = "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/auth/keys";
        IdentityAuthProperties properties = new IdentityAuthProperties(
            new IdentityAuthProperties.Apple("https://appleid.apple.com", "com.verifiedai.learning", jwksUri),
            new IdentityAuthProperties.AccessToken("verified-ai-api", Duration.ofMinutes(15), "", ""),
            new IdentityAuthProperties.RefreshToken(Duration.ofDays(30))
        );
        return new NimbusAppleIdentityVerifier(properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static String signedToken(ECKey key, String subject, String audience, String rawNonce, Instant expiresAt) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer("https://appleid.apple.com")
            .audience(List.of(audience))
            .subject(subject)
            .issueTime(Date.from(NOW))
            .expirationTime(Date.from(expiresAt))
            .claim("nonce", sha256Hex(rawNonce))
            .build();
        SignedJWT jwt = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.getKeyID()).build(),
            claims
        );
        jwt.sign(new ECDSASigner(key.toECPrivateKey()));
        return jwt.serialize();
    }

    private static String sha256Hex(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
