package com.verifiedai.studyplan.rebalance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RebalanceEventRepository extends JpaRepository<RebalanceEventEntity, UUID> {

    Optional<RebalanceEventEntity> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    Optional<RebalanceEventEntity> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    List<RebalanceEventEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<RebalanceEventEntity> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("DELETE FROM RebalanceEventEntity e WHERE e.userId = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);
}
