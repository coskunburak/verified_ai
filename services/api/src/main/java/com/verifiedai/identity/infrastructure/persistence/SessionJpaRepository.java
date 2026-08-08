package com.verifiedai.identity.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SessionJpaRepository extends JpaRepository<SessionJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SessionJpaEntity s where s.id = :id")
    Optional<SessionJpaEntity> findByIdForUpdate(UUID id);

    @Modifying
    @Query("""
        update SessionJpaEntity s
        set s.status = 'REVOKED', s.revokedAt = :now, s.revocationReason = :reason
        where s.userId = :userId and s.revokedAt is null
        """)
    void revokeActiveByUserId(UUID userId, java.time.Instant now, String reason);
}
