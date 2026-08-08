package com.verifiedai.identity.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshTokenJpaEntity t where t.tokenHash = :tokenHash")
    Optional<RefreshTokenJpaEntity> findByTokenHashForUpdate(String tokenHash);

    @Modifying
    @Query("update RefreshTokenJpaEntity t set t.revokedAt = :now where t.sessionId = :sessionId and t.revokedAt is null")
    void revokeActiveBySessionId(UUID sessionId, Instant now);

    @Modifying
    @Query("update RefreshTokenJpaEntity t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    void revokeActiveByUserId(UUID userId, Instant now);
}
