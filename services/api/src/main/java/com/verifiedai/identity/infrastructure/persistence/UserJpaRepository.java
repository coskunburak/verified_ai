package com.verifiedai.identity.infrastructure.persistence;

import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserJpaEntity u where u.id = :id")
    java.util.Optional<UserJpaEntity> findByIdForUpdate(UUID id);
}
