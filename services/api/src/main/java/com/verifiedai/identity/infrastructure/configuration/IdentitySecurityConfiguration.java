package com.verifiedai.identity.infrastructure.configuration;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
@EnableConfigurationProperties(IdentityAuthProperties.class)
class IdentitySecurityConfiguration {

    @Bean
    RsaKeyPair accessTokenKeyPair(IdentityAuthProperties properties, Environment environment) {
        String privateKeyPem = properties.accessToken().privateKeyPem();
        String publicKeyPem = properties.accessToken().publicKeyPem();
        if (isBlank(privateKeyPem)) {
            if (isStrictEnvironment(environment)) {
                throw new IllegalStateException("ACCESS_TOKEN_PRIVATE_KEY_PEM is required outside local/test");
            }
            return generatedDevelopmentKeyPair();
        }
        return keyPairFromPem(privateKeyPem, publicKeyPem);
    }

    @Bean
    JwtEncoder accessTokenJwtEncoder(RsaKeyPair keyPair) {
        RSAKey key = new RSAKey.Builder(keyPair.publicKey())
            .privateKey(keyPair.privateKey())
            .keyID("verified-ai-access-token")
            .build();
        ImmutableJWKSet<SecurityContext> jwkSet = new ImmutableJWKSet<>(new JWKSet(key));
        return new NimbusJwtEncoder(jwkSet);
    }

    @Bean
    JwtDecoder jwtDecoder(RsaKeyPair keyPair) {
        return NimbusJwtDecoder.withPublicKey(keyPair.publicKey()).build();
    }

    private static boolean isStrictEnvironment(Environment environment) {
        String appEnvironment = environment.getProperty("app.environment", "local");
        List<String> activeProfiles = List.of(environment.getActiveProfiles());
        return "prod".equalsIgnoreCase(appEnvironment)
            || "production".equalsIgnoreCase(appEnvironment)
            || "staging".equalsIgnoreCase(appEnvironment)
            || activeProfiles.contains("prod")
            || activeProfiles.contains("staging");
    }

    private static RsaKeyPair generatedDevelopmentKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            java.security.KeyPair keyPair = generator.generateKeyPair();
            return new RsaKeyPair((RSAPublicKey) keyPair.getPublic(), (RSAPrivateKey) keyPair.getPrivate());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate local development access-token key pair", exception);
        }
    }

    private static RsaKeyPair keyPairFromPem(String privateKeyPem, String publicKeyPem) {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            RSAPrivateKey privateKey = (RSAPrivateKey) factory.generatePrivate(
                new PKCS8EncodedKeySpec(decodePem(privateKeyPem))
            );
            RSAPublicKey publicKey;
            if (isBlank(publicKeyPem)) {
                RSAPrivateCrtKey privateCrtKey = (RSAPrivateCrtKey) privateKey;
                publicKey = (RSAPublicKey) factory.generatePublic(
                    new RSAPublicKeySpec(privateCrtKey.getModulus(), privateCrtKey.getPublicExponent())
                );
            } else {
                publicKey = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(decodePem(publicKeyPem)));
            }
            return new RsaKeyPair(publicKey, privateKey);
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid access-token RSA PEM configuration", exception);
        }
    }

    private static byte[] decodePem(String pem) {
        String normalized = pem
            .replace(pemBoundary("BEGIN", "PRIVATE"), "")
            .replace(pemBoundary("END", "PRIVATE"), "")
            .replace(pemBoundary("BEGIN", "PUBLIC"), "")
            .replace(pemBoundary("END", "PUBLIC"), "")
            .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }

    private static String pemBoundary(String marker, String keyType) {
        return "-----" + marker + " " + keyType + " KEY-----";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
