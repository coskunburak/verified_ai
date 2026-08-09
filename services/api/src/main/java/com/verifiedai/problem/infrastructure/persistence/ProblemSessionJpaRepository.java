package com.verifiedai.problem.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemSessionJpaRepository extends JpaRepository<ProblemSessionJpaEntity, UUID> {
    Optional<ProblemSessionJpaEntity> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from ProblemSessionJpaEntity session where session.id = :id and session.userId = :userId")
    Optional<ProblemSessionJpaEntity> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);
}
