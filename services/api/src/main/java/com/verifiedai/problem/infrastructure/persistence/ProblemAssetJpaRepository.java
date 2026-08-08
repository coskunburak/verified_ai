package com.verifiedai.problem.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemAssetJpaRepository extends JpaRepository<ProblemAssetJpaEntity, UUID> {
    Optional<ProblemAssetJpaEntity> findByUserIdAndReservationIdempotencyKey(UUID userId, String reservationIdempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select asset from ProblemAssetJpaEntity asset where asset.id = :id and asset.userId = :userId")
    Optional<ProblemAssetJpaEntity> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    List<ProblemAssetJpaEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<ProblemAssetJpaEntity> findByStatusAndUploadExpiresAtBeforeOrderByUploadExpiresAtAsc(
        String status,
        Instant uploadExpiresAt,
        Pageable pageable
    );
}
