package com.verifiedai.studyplan.practice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PracticeSessionItemRepository extends JpaRepository<PracticeSessionItemEntity, UUID> {

    Optional<PracticeSessionItemEntity> findByIdAndUserId(UUID id, UUID userId);

    List<PracticeSessionItemEntity> findAllBySessionIdOrderByOrdinalIndexAsc(UUID sessionId);

    List<PracticeSessionItemEntity> findAllByUserIdAndSkillIdOrderByCreatedAtDesc(UUID userId, String skillId);
}
