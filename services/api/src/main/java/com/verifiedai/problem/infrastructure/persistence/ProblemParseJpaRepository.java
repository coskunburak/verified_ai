package com.verifiedai.problem.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemParseJpaRepository extends JpaRepository<ProblemParseJpaEntity, UUID> {
    Optional<ProblemParseJpaEntity> findByParseJobId(UUID parseJobId);

    Optional<ProblemParseJpaEntity> findByIdAndUserIdAndProblemSessionId(UUID id, UUID userId, UUID problemSessionId);

    List<ProblemParseJpaEntity> findByIdInAndUserId(List<UUID> ids, UUID userId);

    Optional<ProblemParseJpaEntity> findByCorrectionIdempotencyKeyAndUserIdAndProblemSessionId(
        String correctionIdempotencyKey,
        UUID userId,
        UUID problemSessionId
    );

    List<ProblemParseJpaEntity> findByProblemSessionIdInAndUserIdOrderByRevisionDesc(
        List<UUID> problemSessionIds,
        UUID userId
    );

    Optional<ProblemParseJpaEntity> findFirstByProblemSessionIdAndUserIdOrderByRevisionDesc(UUID problemSessionId, UUID userId);

    List<ProblemParseJpaEntity> findAllByProblemSessionIdAndUserIdOrderByRevisionDesc(UUID problemSessionId, UUID userId);

    long countByProblemSessionIdAndUserId(UUID problemSessionId, UUID userId);

    long countByProblemSessionIdAndUserIdAndSourceAndCreatedAtGreaterThanEqual(
        UUID problemSessionId,
        UUID userId,
        String source,
        Instant createdAt
    );

    @Query("""
        select coalesce(max(parse.revision), 0)
        from ProblemParseJpaEntity parse
        where parse.problemSessionId = :problemSessionId
        """)
    int maxRevision(@Param("problemSessionId") UUID problemSessionId);

    @Query("""
        select distinct parse.problemSessionId
        from ProblemParseJpaEntity parse
        where parse.userId = :userId
          and parse.problemSessionId in :problemSessionIds
          and parse.supportStatus in ('SUPPORTED', 'REVIEW_REQUIRED')
        """)
    List<UUID> findAcceptedParseSessionIds(
        @Param("userId") UUID userId,
        @Param("problemSessionIds") List<UUID> problemSessionIds
    );
}
