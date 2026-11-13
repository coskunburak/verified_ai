package com.verifiedai.studyplan.coach;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyCoachSessionRepository extends JpaRepository<DailyCoachSessionEntity, UUID> {

    Optional<DailyCoachSessionEntity> findByUserIdAndLocalDate(UUID userId, LocalDate localDate);

    Optional<DailyCoachSessionEntity> findByIdAndUserId(UUID id, UUID userId);

    List<DailyCoachSessionEntity> findAllByUserIdOrderByLocalDateDesc(UUID userId);

    Optional<DailyCoachSessionEntity> findFirstByUserIdOrderByLocalDateDesc(UUID userId);

    List<DailyCoachSessionEntity> findByUserIdAndLocalDateBeforeAndStatusIn(
            UUID userId,
            LocalDate localDate,
            java.util.Collection<DailyCoachSessionStatus> statuses
    );

    @Modifying
    @Query("""
            UPDATE DailyCoachSessionEntity s
            SET s.preferredMode = :preferredMode,
                s.budgetMinutes = :budgetMinutes,
                s.updatedAt = :updatedAt
            WHERE s.id = :sessionId AND s.userId = :userId
            """)
    int updatePreferences(
            @Param("sessionId") UUID sessionId,
            @Param("userId") UUID userId,
            @Param("preferredMode") DailyCoachMode preferredMode,
            @Param("budgetMinutes") int budgetMinutes,
            @Param("updatedAt") Instant updatedAt
    );

    @Modifying
    @Query("""
            UPDATE DailyCoachSessionEntity s
            SET s.completedTasksCount = :completedTasksCount,
                s.totalTasksCount = :totalTasksCount,
                s.status = :status,
                s.updatedAt = :updatedAt
            WHERE s.id = :sessionId AND s.userId = :userId
            """)
    int updateProgress(
            @Param("sessionId") UUID sessionId,
            @Param("userId") UUID userId,
            @Param("completedTasksCount") int completedTasksCount,
            @Param("totalTasksCount") int totalTasksCount,
            @Param("status") DailyCoachSessionStatus status,
            @Param("updatedAt") Instant updatedAt
    );

    @Modifying
    @Query("DELETE FROM DailyCoachSessionEntity s WHERE s.userId = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
