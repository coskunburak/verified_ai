package com.verifiedai.problem.infrastructure.persistence.repository;

import com.verifiedai.problem.infrastructure.persistence.entity.ProblemAssetQualityEvidenceJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemAssetQualityEvidenceJpaRepository extends JpaRepository<ProblemAssetQualityEvidenceJpaEntity, UUID> {
    List<ProblemAssetQualityEvidenceJpaEntity> findBySourceAssetIdAndUserIdOrderByCreatedAtAsc(UUID sourceAssetId, UUID userId);
}
