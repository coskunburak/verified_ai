package com.verifiedai.exam.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface AssembledAssessmentRepository {

    AssembledAssessment save(AssembledAssessment assessment);

    Optional<AssembledAssessment> findById(UUID id);

    Optional<AssembledAssessment> findByUserIdAndBlueprintIdAndSeed(UUID userId, UUID blueprintId, String assemblySeed);

    List<AssembledAssessment> findByUserId(UUID userId, int limit);

    Set<UUID> findRecentlySeenQuestionIds(UUID userId, UUID examDefinitionId, Instant since);

    int deleteByUserId(UUID userId);
}
