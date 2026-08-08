package com.verifiedai.profile.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningProfileJpaRepository extends JpaRepository<LearningProfileJpaEntity, UUID> {
    Optional<LearningProfileJpaEntity> findByUserId(UUID userId);
}
