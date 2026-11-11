package com.verifiedai.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LearnerMasteryCalibrationRepository extends JpaRepository<LearnerMasteryCalibrationRecordEntity, UUID> {

    Optional<LearnerMasteryCalibrationRecordEntity> findByAttemptIdAndCanonicalSkillCode(
        UUID attemptId,
        String canonicalSkillCode
    );

    List<LearnerMasteryCalibrationRecordEntity> findByCreatedAtBetweenOrderByCreatedAtDesc(
        Instant from,
        Instant to
    );

    List<LearnerMasteryCalibrationRecordEntity> findByCanonicalSkillCodeAndCreatedAtBetweenOrderByCreatedAtDesc(
        String canonicalSkillCode,
        Instant from,
        Instant to
    );

    List<LearnerMasteryCalibrationRecordEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Modifying
    @Query("DELETE FROM LearnerMasteryCalibrationRecordEntity r WHERE r.userId = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);
}
