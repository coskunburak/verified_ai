package com.verifiedai.identity.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_identities")
public class UserIdentityJpaEntity {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String providerSubject;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected UserIdentityJpaEntity() {
    }

    private UserIdentityJpaEntity(UUID id, UUID userId, String provider, String providerSubject, Instant now) {
        this.id = id;
        this.userId = userId;
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static UserIdentityJpaEntity apple(UUID userId, String providerSubject, Instant now) {
        return of(userId, "APPLE", providerSubject, now);
    }

    public static UserIdentityJpaEntity email(UUID userId, String providerSubject, Instant now) {
        return of(userId, "EMAIL", providerSubject, now);
    }

    public static UserIdentityJpaEntity guest(UUID userId, String providerSubject, Instant now) {
        return of(userId, "GUEST", providerSubject, now);
    }

    private static UserIdentityJpaEntity of(UUID userId, String provider, String providerSubject, Instant now) {
        return new UserIdentityJpaEntity(UUID.randomUUID(), userId, provider, providerSubject, now);
    }

    public UUID userId() {
        return userId;
    }
}
