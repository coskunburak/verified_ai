package com.verifiedai.identity.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserJpaEntity {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant deletionRequestedAt;
    private Instant deletedAt;

    protected UserJpaEntity() {
    }

    private UserJpaEntity(UUID id, Instant now) {
        this.id = id;
        this.status = "ACTIVE";
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static UserJpaEntity active(UUID id, Instant now) {
        return new UserJpaEntity(id, now);
    }

    public boolean active() {
        return "ACTIVE".equals(status);
    }

    public boolean deletionRequested() {
        return "DELETION_REQUESTED".equals(status);
    }

    public boolean deleted() {
        return "DELETED".equals(status);
    }

    public void requestDeletion(Instant now) {
        if ("DELETED".equals(status)) {
            return;
        }
        this.status = "DELETION_REQUESTED";
        this.deletionRequestedAt = now;
        this.updatedAt = now;
    }

    public void markDeletionInProgress(Instant now) {
        this.status = "DELETION_IN_PROGRESS";
        this.updatedAt = now;
    }

    public void markDeleted(Instant now) {
        this.status = "DELETED";
        if (deletionRequestedAt == null) {
            deletionRequestedAt = now;
        }
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public UUID id() {
        return id;
    }

    public String status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant deletionRequestedAt() {
        return deletionRequestedAt;
    }

    public Instant deletedAt() {
        return deletedAt;
    }
}
