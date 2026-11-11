package com.verifiedai.mastery.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CounterfactualTwinSubmissionRepository extends JpaRepository<CounterfactualTwinSubmissionEntity, UUID> {

    List<CounterfactualTwinSubmissionEntity> findAllByTenantIdAndUserIdAndTwinIdOrderBySubmittedAtDesc(
            UUID tenantId, UUID userId, UUID twinId
    );
}
