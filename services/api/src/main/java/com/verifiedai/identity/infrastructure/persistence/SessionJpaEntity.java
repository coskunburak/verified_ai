package com.verifiedai.identity.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sessions")
public class SessionJpaEntity {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant lastSeenAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;
    private String revocationReason;

    protected SessionJpaEntity() {
    }

    private SessionJpaEntity(UUID id, UUID userId, Instant now, Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.status = "ACTIVE";
        this.createdAt = now;
        this.lastSeenAt = now;
        this.expiresAt = expiresAt;
    }

    public static SessionJpaEntity active(UUID userId, Instant now, Instant expiresAt) {
        return new SessionJpaEntity(UUID.randomUUID(), userId, now, expiresAt);
    }

    public boolean activeAt(Instant now) {
        return revokedAt == null && "ACTIVE".equals(status) && expiresAt.isAfter(now);
    }

    public void touch(Instant now) {
        this.lastSeenAt = now;
    }

    public void revoke(Instant now, String reason) {
        if (revokedAt == null) {
            this.status = "REVOKED";
            this.revokedAt = now;
            this.revocationReason = reason;
        }
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public Instant expiresAt() {
        return expiresAt;
    }
}
