package com.verifiedai.identity.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_password_credentials")
public class UserPasswordCredentialJpaEntity {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String emailNormalized;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String passwordAlgorithm;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant lastUsedAt;

    protected UserPasswordCredentialJpaEntity() {
    }

    private UserPasswordCredentialJpaEntity(UUID id, UUID userId, String emailNormalized, String passwordHash, Instant now) {
        this.id = id;
        this.userId = userId;
        this.emailNormalized = emailNormalized;
        this.passwordHash = passwordHash;
        this.passwordAlgorithm = "BCRYPT";
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static UserPasswordCredentialJpaEntity issue(UUID userId, String emailNormalized, String passwordHash, Instant now) {
        return new UserPasswordCredentialJpaEntity(UUID.randomUUID(), userId, emailNormalized, passwordHash, now);
    }

    public void markUsed(Instant now) {
        this.lastUsedAt = now;
        this.updatedAt = now;
    }

    public UUID userId() {
        return userId;
    }

    public String passwordHash() {
        return passwordHash;
    }
}
