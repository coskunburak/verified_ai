package com.verifiedai.problem.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemClassificationJpaRepository extends JpaRepository<ProblemClassificationJpaEntity, UUID> {
    Optional<ProblemClassificationJpaEntity> findFirstByProblemSessionIdAndUserIdOrderByRevisionDesc(
        UUID problemSessionId,
        UUID userId
    );

    Optional<ProblemClassificationJpaEntity> findByCanonicalProblemIdAndOntologyVersionAndSchemaVersion(
        UUID canonicalProblemId,
        String ontologyVersion,
        String schemaVersion
    );

    @Query("""
        select coalesce(max(classification.revision), 0)
        from ProblemClassificationJpaEntity classification
        where classification.problemSessionId = :problemSessionId
        """)
    int maxRevision(@Param("problemSessionId") UUID problemSessionId);
}
