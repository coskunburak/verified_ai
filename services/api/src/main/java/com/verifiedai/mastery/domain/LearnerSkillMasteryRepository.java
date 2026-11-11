package com.verifiedai.mastery.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LearnerSkillMasteryRepository extends JpaRepository<LearnerSkillMasteryEntity, LearnerSkillMasteryId> {

    @Query("SELECT m FROM LearnerSkillMasteryEntity m WHERE (m.tenantId = :tenantId OR m.tenantId IS NULL) AND m.userId = :userId AND (m.curriculumCode = :curriculumCode OR m.curriculumCode IS NULL)")
    List<LearnerSkillMasteryEntity> findAllByTenantIdAndUserIdAndCurriculumCode(
            @Param("tenantId") UUID tenantId,
            @Param("userId") UUID userId,
            @Param("curriculumCode") String curriculumCode
    );

    @Query("SELECT m FROM LearnerSkillMasteryEntity m WHERE (m.tenantId = :tenantId OR m.tenantId IS NULL) AND m.userId = :userId AND m.skillCode = :skillCode")
    Optional<LearnerSkillMasteryEntity> findByTenantIdAndUserIdAndSkillCode(
            @Param("tenantId") UUID tenantId,
            @Param("userId") UUID userId,
            @Param("skillCode") String skillCode
    );
}
