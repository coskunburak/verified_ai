package com.verifiedai.identity.application;

import com.verifiedai.identity.infrastructure.persistence.AuthSecurityEventJpaEntity;
import com.verifiedai.identity.infrastructure.persistence.AuthSecurityEventJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
final class AuthSecurityEventRecorder {
    private final AuthSecurityEventJpaRepository repository;
    private final Clock clock;
    private IdentitySecurityNotificationService securityNotificationService;

    AuthSecurityEventRecorder(AuthSecurityEventJpaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setSecurityNotificationService(IdentitySecurityNotificationService securityNotificationService) {
        this.securityNotificationService = securityNotificationService;
    }

    void record(String eventType, UUID userId, UUID sessionId, String reason) {
        Instant now = clock.instant();
        AuthSecurityEventJpaEntity entity = new AuthSecurityEventJpaEntity(eventType, userId, sessionId, reason, now);
        repository.save(entity);
        if (securityNotificationService == null || userId == null || eventType == null) {
            return;
        }
        String mappedType = switch (eventType) {
            case "REFRESH_REUSE_DETECTED", "REFRESH_TOKEN_REUSE" -> "REFRESH_TOKEN_REUSE";
            case "SESSION_REVOKED_ALL" -> "SESSION_REVOKED_ALL";
            default -> null;
        };
        if (mappedType != null) {
            try {
                securityNotificationService.notifySecurityEvent(userId, mappedType, entity.id().toString(), now);
            } catch (RuntimeException ignored) {
            }
        }
    }
}
