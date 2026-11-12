package com.verifiedai.studyplan.domain;

import org.springframework.data.domain.Pageable;
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
public interface LearnerRecommendationRepository extends JpaRepository<LearnerRecommendationEntity, UUID> {

    List<LearnerRecommendationEntity> findByUserIdAndStatusAndExpiresAtAfterOrderByPriorityScoreDescTargetSkillCodeAsc(
            UUID userId,
            RecommendationStatus status,
            Instant now,
            Pageable pageable
    );

    Optional<LearnerRecommendationEntity> findByIdAndUserId(UUID id, UUID userId);

    List<LearnerRecommendationEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    @Modifying
    @Query("UPDATE LearnerRecommendationEntity r SET r.status = com.verifiedai.studyplan.domain.RecommendationStatus.EXPIRED, r.updatedAt = :now WHERE r.userId = :userId AND r.status = com.verifiedai.studyplan.domain.RecommendationStatus.ACTIVE AND r.expiresAt <= :now")
    int expireOutdatedByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM LearnerRecommendationEntity r WHERE r.userId = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);
}
