package com.verifiedai.problem.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecognitionEvidenceJpaRepository extends JpaRepository<RecognitionEvidenceJpaEntity, UUID> {
    Optional<RecognitionEvidenceJpaEntity> findByRecognitionJobId(UUID recognitionJobId);

    Optional<RecognitionEvidenceJpaEntity> findByIdAndUserId(UUID id, UUID userId);

    Optional<RecognitionEvidenceJpaEntity> findFirstByProblemSessionIdAndUserIdOrderByCreatedAtDesc(UUID problemSessionId, UUID userId);

    List<RecognitionEvidenceJpaEntity> findByProblemSessionIdInAndUserIdOrderByCreatedAtDesc(
        List<UUID> problemSessionIds,
        UUID userId
    );

    @Query("""
        select coalesce(max(evidence.revision), 0)
        from RecognitionEvidenceJpaEntity evidence
        where evidence.problemSessionId = :problemSessionId
          and evidence.inputDerivativeId = :inputDerivativeId
          and evidence.schemaVersion = :schemaVersion
          and evidence.promptVersion = :promptVersion
        """)
    int maxRevision(
        @Param("problemSessionId") UUID problemSessionId,
        @Param("inputDerivativeId") UUID inputDerivativeId,
        @Param("schemaVersion") String schemaVersion,
        @Param("promptVersion") String promptVersion
    );
}
