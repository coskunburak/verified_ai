package com.verifiedai.studyplan.practice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PracticeSessionRepository extends JpaRepository<PracticeSessionEntity, UUID> {

    Optional<PracticeSessionEntity> findByIdAndUserId(UUID id, UUID userId);

    List<PracticeSessionEntity> findAllByUserIdOrderByStartedAtDesc(UUID userId);

    @Modifying
    @Query("DELETE FROM PracticeSessionEntity s WHERE s.userId = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
