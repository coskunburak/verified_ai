package com.verifiedai.studyplan.spaced;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpacedReviewQueueRepository extends JpaRepository<SpacedReviewQueueEntity, UUID> {

    @Query("""
            SELECT q FROM SpacedReviewQueueEntity q
            WHERE q.userId = :userId
              AND q.status IN ('PENDING', 'SERVED')
              AND q.dueAt <= :now
            ORDER BY q.dueAt ASC
            """)
    List<SpacedReviewQueueEntity> findActiveDueByUserId(
            @Param("userId") UUID userId,
            @Param("now") Instant now
    );

    @Query("""
            SELECT q FROM SpacedReviewQueueEntity q
            WHERE q.userId = :userId
              AND q.status IN :statuses
            ORDER BY q.dueAt ASC
            """)
    List<SpacedReviewQueueEntity> findByUserIdAndStatusIn(
            @Param("userId") UUID userId,
            @Param("statuses") Collection<SpacedQueueStatus> statuses
    );

    Optional<SpacedReviewQueueEntity> findByIdAndUserId(UUID id, UUID userId);

    @Query("""
            SELECT q FROM SpacedReviewQueueEntity q
            WHERE q.userId = :userId
              AND q.canonicalSkillCode = :skillCode
              AND q.triggerType = :triggerType
              AND ((:mistakeCode IS NULL AND q.targetMistakeCode IS NULL) OR q.targetMistakeCode = :mistakeCode)
              AND q.status IN ('PENDING', 'SERVED')
            """)
    Optional<SpacedReviewQueueEntity> findActiveByUserAndSkillAndTrigger(
            @Param("userId") UUID userId,
            @Param("skillCode") String canonicalSkillCode,
            @Param("triggerType") SpacedTriggerType triggerType,
            @Param("mistakeCode") String targetMistakeCode
    );

    List<SpacedReviewQueueEntity> findAllByUserId(UUID userId);

    @Modifying
    @Query("DELETE FROM SpacedReviewQueueEntity q WHERE q.userId = :userId")
    int purgeAllByUserId(@Param("userId") UUID userId);
}
