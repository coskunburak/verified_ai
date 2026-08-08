package com.verifiedai.identity.application;

import com.verifiedai.identity.infrastructure.persistence.AuthSecurityEventJpaEntity;
import com.verifiedai.identity.infrastructure.persistence.AuthSecurityEventJpaRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
final class AuthSecurityEventRecorder {
    private final AuthSecurityEventJpaRepository repository;
    private final Clock clock;

    AuthSecurityEventRecorder(AuthSecurityEventJpaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    void record(String eventType, UUID userId, UUID sessionId, String reason) {
        repository.save(new AuthSecurityEventJpaEntity(eventType, userId, sessionId, reason, clock.instant()));
    }
}
