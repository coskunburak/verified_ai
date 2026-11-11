package com.verifiedai.mastery.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CounterfactualTwinRepository extends JpaRepository<CounterfactualTwinDrillEntity, UUID> {

    @Query("SELECT t FROM CounterfactualTwinDrillEntity t WHERE t.tenantId = :tenantId AND t.userId = :userId AND t.sourceAttemptId = :sourceAttemptId AND t.status = 'ISSUED'")
    Optional<CounterfactualTwinDrillEntity> findActiveIssuedBySourceAttempt(
            @Param("tenantId") UUID tenantId,
            @Param("userId") UUID userId,
            @Param("sourceAttemptId") UUID sourceAttemptId
    );

    Optional<CounterfactualTwinDrillEntity> findByTenantIdAndUserIdAndTwinId(UUID tenantId, UUID userId, UUID twinId);
}
