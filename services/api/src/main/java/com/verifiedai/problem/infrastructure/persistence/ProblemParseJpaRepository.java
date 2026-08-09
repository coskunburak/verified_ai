package com.verifiedai.problem.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemParseJpaRepository extends JpaRepository<ProblemParseJpaEntity, UUID> {
    Optional<ProblemParseJpaEntity> findByParseJobId(UUID parseJobId);

    Optional<ProblemParseJpaEntity> findFirstByProblemSessionIdAndUserIdOrderByRevisionDesc(UUID problemSessionId, UUID userId);

    @Query("""
        select coalesce(max(parse.revision), 0)
        from ProblemParseJpaEntity parse
        where parse.problemSessionId = :problemSessionId
        """)
    int maxRevision(@Param("problemSessionId") UUID problemSessionId);
}
