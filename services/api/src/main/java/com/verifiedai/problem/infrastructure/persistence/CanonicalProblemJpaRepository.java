package com.verifiedai.problem.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CanonicalProblemJpaRepository extends JpaRepository<CanonicalProblemJpaEntity, UUID> {
    Optional<CanonicalProblemJpaEntity> findFirstByProblemSessionIdAndUserIdOrderByCanonicalRevisionDesc(
        UUID problemSessionId,
        UUID userId
    );

    Optional<CanonicalProblemJpaEntity> findByProblemParseIdAndProblemParseRevisionAndSchemaVersion(
        UUID problemParseId,
        int problemParseRevision,
        String schemaVersion
    );

    @Query("""
        select coalesce(max(canonical.canonicalRevision), 0)
        from CanonicalProblemJpaEntity canonical
        where canonical.problemSessionId = :problemSessionId
        """)
    int maxCanonicalRevision(@Param("problemSessionId") UUID problemSessionId);
}
