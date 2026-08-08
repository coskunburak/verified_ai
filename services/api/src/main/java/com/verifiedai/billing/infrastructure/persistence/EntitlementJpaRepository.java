package com.verifiedai.billing.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntitlementJpaRepository extends JpaRepository<EntitlementJpaEntity, UUID> {
    Optional<EntitlementJpaEntity> findByUserId(UUID userId);
}
