package com.verifiedai.studyplan.coach;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface DailyCoachTaskRepository extends JpaRepository<DailyCoachTaskEntity, UUID> {

    List<DailyCoachTaskEntity> findAllBySessionIdOrderByTaskOrderAsc(UUID sessionId);

    @Modifying
    @Query("""
            UPDATE DailyCoachTaskEntity t
            SET t.status = com.verifiedai.studyplan.coach.DailyCoachTaskStatus.COMPLETED,
                t.completedAt = :completedAt
            WHERE t.id = :taskId AND t.sessionId = :sessionId
            """)
    int markTaskCompleted(
            @Param("sessionId") UUID sessionId,
            @Param("taskId") UUID taskId,
            @Param("completedAt") Instant completedAt
    );

    @Modifying
    @Query("""
            UPDATE DailyCoachTaskEntity t
            SET t.status = com.verifiedai.studyplan.coach.DailyCoachTaskStatus.SKIPPED
            WHERE t.sessionId = :sessionId
              AND t.taskOrder > :maxOrder
              AND t.status = com.verifiedai.studyplan.coach.DailyCoachTaskStatus.PENDING
            """)
    int skipTasksAfterOrder(
            @Param("sessionId") UUID sessionId,
            @Param("maxOrder") int maxOrder
    );
}
