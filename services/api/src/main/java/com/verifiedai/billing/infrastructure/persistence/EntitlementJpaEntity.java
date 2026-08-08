package com.verifiedai.billing.infrastructure.persistence;

import com.verifiedai.billing.domain.model.AppStoreEnvironment;
import com.verifiedai.billing.domain.model.EntitlementSource;
import com.verifiedai.billing.domain.model.EntitlementStatus;
import com.verifiedai.billing.domain.model.EntitlementTier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "entitlements")
public class EntitlementJpaEntity {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String tier;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private Instant effectiveAt;

    private Instant expiresAt;

    private String originalTransactionId;

    private String environment;

    private Instant lastVerifiedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected EntitlementJpaEntity() {
    }

    private EntitlementJpaEntity(UUID userId, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.tier = EntitlementTier.FREE.name();
        this.source = EntitlementSource.DEFAULT_FREE.name();
        this.status = EntitlementStatus.ACTIVE.name();
        this.effectiveAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static EntitlementJpaEntity defaultFree(UUID userId, Instant now) {
        return new EntitlementJpaEntity(userId, now);
    }

    public void applyAppStoreSubscription(
        EntitlementTier tier,
        EntitlementStatus status,
        Instant effectiveAt,
        Instant expiresAt,
        String originalTransactionId,
        AppStoreEnvironment environment,
        Instant verifiedAt
    ) {
        this.tier = tier.name();
        this.source = EntitlementSource.APP_STORE_SUBSCRIPTION.name();
        this.status = status.name();
        this.effectiveAt = effectiveAt;
        this.expiresAt = expiresAt;
        this.originalTransactionId = originalTransactionId;
        this.environment = environment.name();
        this.lastVerifiedAt = verifiedAt;
        this.updatedAt = verifiedAt;
    }

    public void applyDefaultFree(Instant now) {
        this.tier = EntitlementTier.FREE.name();
        this.source = EntitlementSource.DEFAULT_FREE.name();
        this.status = EntitlementStatus.ACTIVE.name();
        if (effectiveAt == null) {
            effectiveAt = now;
        }
        this.expiresAt = null;
        this.originalTransactionId = null;
        this.environment = null;
        this.lastVerifiedAt = null;
        this.updatedAt = now;
    }

    public void applyAccountDeleted(Instant now) {
        this.tier = EntitlementTier.FREE.name();
        this.source = EntitlementSource.DEFAULT_FREE.name();
        this.status = EntitlementStatus.REVOKED.name();
        this.effectiveAt = now;
        this.expiresAt = null;
        this.originalTransactionId = null;
        this.environment = null;
        this.lastVerifiedAt = null;
        this.updatedAt = now;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public String tier() {
        return tier;
    }

    public String source() {
        return source;
    }

    public String status() {
        return status;
    }

    public Instant effectiveAt() {
        return effectiveAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public String originalTransactionId() {
        return originalTransactionId;
    }

    public String environment() {
        return environment;
    }

    public Instant lastVerifiedAt() {
        return lastVerifiedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Long version() {
        return version;
    }
}
