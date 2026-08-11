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

public interface ProblemParseJobJpaRepository extends JpaRepository<ProblemParseJobJpaEntity, UUID> {
    Optional<ProblemParseJobJpaEntity> findFirstByProblemSessionIdAndUserIdOrderByCreatedAtDesc(UUID problemSessionId, UUID userId);

    List<ProblemParseJobJpaEntity> findByProblemSessionIdInAndUserIdOrderByCreatedAtDesc(
        List<UUID> problemSessionIds,
        UUID userId
    );

    Optional<ProblemParseJobJpaEntity> findByUserIdAndProblemSessionIdAndRecognitionEvidenceIdAndRecognitionEvidenceRevisionAndCapabilityAndPromptIdAndPromptVersionAndSchemaVersionAndRoutePolicyVersion(
        UUID userId,
        UUID problemSessionId,
        UUID recognitionEvidenceId,
        int recognitionEvidenceRevision,
        String capability,
        String promptId,
        String promptVersion,
        String schemaVersion,
        String routePolicyVersion
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from ProblemParseJobJpaEntity job where job.id = :id")
    Optional<ProblemParseJobJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
        select job.id from ProblemParseJobJpaEntity job
        where job.status in ('QUEUED', 'FAILED_RETRYABLE') and job.nextAttemptAt <= :now
        order by job.nextAttemptAt asc, job.createdAt asc
        """)
    List<UUID> findDueJobIds(@Param("now") Instant now, Pageable pageable);

    @Query("""
        select job.id from ProblemParseJobJpaEntity job
        where job.status = 'RUNNING' and job.updatedAt < :staleBefore
        order by job.updatedAt asc
        """)
    List<UUID> findStaleRunningJobIds(@Param("staleBefore") Instant staleBefore, Pageable pageable);
}
