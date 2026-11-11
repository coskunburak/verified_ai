package com.verifiedai.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Objects;
import java.util.UUID;

public record VerifiedJwtPrincipal(UUID tenantId, UUID userId) {
    public VerifiedJwtPrincipal {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(userId, "userId cannot be null");
    }

    public static VerifiedJwtPrincipal from(Jwt jwt) {
        Objects.requireNonNull(jwt, "jwt cannot be null");
        UUID user = UUID.fromString(jwt.getSubject());
        String tenantClaim = jwt.getClaimAsString("tenant_id");
        UUID tenant = (tenantClaim != null && !tenantClaim.isBlank())
                ? UUID.fromString(tenantClaim)
                : user;
        return new VerifiedJwtPrincipal(tenant, user);
    }
}
