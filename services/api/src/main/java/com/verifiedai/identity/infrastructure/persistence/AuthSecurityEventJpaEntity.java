package com.verifiedai.identity.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_security_events")
public class AuthSecurityEventJpaEntity {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String eventType;

    private UUID userId;
    private UUID sessionId;
    private String reason;

    @Column(nullable = false)
    private Instant createdAt;

    protected AuthSecurityEventJpaEntity() {
    }

    public AuthSecurityEventJpaEntity(String eventType, UUID userId, UUID sessionId, String reason, Instant now) {
        this.id = UUID.randomUUID();
        this.eventType = eventType;
        this.userId = userId;
        this.sessionId = sessionId;
        this.reason = reason;
        this.createdAt = now;
    }
}
