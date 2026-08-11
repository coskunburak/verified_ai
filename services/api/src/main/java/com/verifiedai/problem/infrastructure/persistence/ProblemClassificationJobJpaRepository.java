package com.verifiedai.problem.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemClassificationJobJpaRepository
    extends JpaRepository<ProblemClassificationJobJpaEntity, UUID> {

    Optional<ProblemClassificationJobJpaEntity>
        findFirstByProblemSessionIdAndUserIdOrderByCreatedAtDesc(
            UUID problemSessionId,
            UUID userId
        );

    Optional<ProblemClassificationJobJpaEntity>
    findFirstByCanonicalProblemIdAndUserIdOrderByCreatedAtDesc(
        UUID canonicalProblemId,
        UUID userId
    );

    List<ProblemClassificationJobJpaEntity>
    findByCanonicalProblemIdInAndUserIdOrderByCreatedAtDesc(
        List<UUID> canonicalProblemIds,
        UUID userId
    );

    Optional<ProblemClassificationJobJpaEntity>
        findByRequestFingerprint(
            String requestFingerprint
        );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select job
        from ProblemClassificationJobJpaEntity job
        where job.id = :id
        """)
    Optional<ProblemClassificationJobJpaEntity> findByIdForUpdate(
        @Param("id") UUID id
    );

    @Query("""
        select job.id
        from ProblemClassificationJobJpaEntity job
        where job.status in ('QUEUED', 'FAILED_RETRYABLE')
          and job.nextAttemptAt <= :now
        order by job.nextAttemptAt asc, job.createdAt asc
        """)
    List<UUID> findDueJobIds(
        @Param("now") Instant now,
        Pageable pageable
    );

    @Query("""
        select job.id
        from ProblemClassificationJobJpaEntity job
        where job.status = 'RUNNING'
          and job.updatedAt < :staleBefore
        order by job.updatedAt asc
        """)
    List<UUID> findStaleRunningJobIds(
        @Param("staleBefore") Instant staleBefore,
        Pageable pageable
    );
}
