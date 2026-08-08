package com.verifiedai.identity.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenJpaEntity {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID sessionId;

    @Column(nullable = false)
    private UUID familyId;

    @Column(nullable = false)
    private String tokenHash;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant usedAt;
    private Instant revokedAt;
    private UUID replacedById;

    protected RefreshTokenJpaEntity() {
    }

    private RefreshTokenJpaEntity(UUID id, UUID userId, UUID sessionId, UUID familyId, String tokenHash, Instant now, Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.sessionId = sessionId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public static RefreshTokenJpaEntity issue(UUID userId, UUID sessionId, UUID familyId, String tokenHash, Instant now, Instant expiresAt) {
        return new RefreshTokenJpaEntity(UUID.randomUUID(), userId, sessionId, familyId, tokenHash, now, expiresAt);
    }

    public boolean validAt(Instant now) {
        return revokedAt == null && usedAt == null && replacedById == null && expiresAt.isAfter(now);
    }

    public boolean consumedOrReused() {
        return usedAt != null || replacedById != null;
    }

    public void consume(UUID replacementId, Instant now) {
        this.usedAt = now;
        this.replacedById = replacementId;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            this.revokedAt = now;
        }
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public UUID familyId() {
        return familyId;
    }

    public Instant expiresAt() {
        return expiresAt;
    }
}
