package com.verifiedai.problem.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemAssetDerivativeJpaRepository extends JpaRepository<ProblemAssetDerivativeJpaEntity, UUID> {
    List<ProblemAssetDerivativeJpaEntity> findBySourceAssetIdAndUserIdOrderByCreatedAtDesc(UUID sourceAssetId, UUID userId);

    List<ProblemAssetDerivativeJpaEntity> findByProblemSessionIdInAndUserIdOrderByCreatedAtDesc(
        List<UUID> problemSessionIds,
        UUID userId
    );

    Optional<ProblemAssetDerivativeJpaEntity> findBySourceAssetIdAndUserIdAndDerivativeKindAndProcessorNameAndProcessorVersionAndConfigurationVersion(
        UUID sourceAssetId,
        UUID userId,
        String derivativeKind,
        String processorName,
        String processorVersion,
        String configurationVersion
    );

    Optional<ProblemAssetDerivativeJpaEntity> findFirstByProblemSessionIdAndUserIdAndSelectedForRecognitionTrueAndStatusOrderByCreatedAtDesc(
        UUID problemSessionId,
        UUID userId,
        String status
    );
}
