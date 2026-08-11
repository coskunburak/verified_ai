package com.verifiedai.problem.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemSessionJpaRepository extends JpaRepository<ProblemSessionJpaEntity, UUID> {
    Optional<ProblemSessionJpaEntity> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from ProblemSessionJpaEntity session where session.id = :id and session.userId = :userId")
    Optional<ProblemSessionJpaEntity> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("""
        select session
        from ProblemSessionJpaEntity session
        where session.userId = :userId
        order by session.updatedAt desc, session.id desc
        """)
    List<ProblemSessionJpaEntity> findHistoryFirstPage(@Param("userId") UUID userId, Pageable pageable);

    @Query(
        value = """
            select *
            from problem_sessions
            where user_id = :userId
              and (
                updated_at < :updatedAt
                or (updated_at = :updatedAt and id < :sessionId)
              )
            order by updated_at desc, id desc
            """,
        nativeQuery = true
    )
    List<ProblemSessionJpaEntity> findHistoryAfter(
        @Param("userId") UUID userId,
        @Param("updatedAt") Instant updatedAt,
        @Param("sessionId") UUID sessionId,
        Pageable pageable
    );
}
