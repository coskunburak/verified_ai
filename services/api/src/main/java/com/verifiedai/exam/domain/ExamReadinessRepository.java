package com.verifiedai.exam.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence boundary for Sprint 9.6 Exam Readiness Projections and topic ROI allocations.
 */
public interface ExamReadinessRepository {

    ExamReadinessProjection saveUpsert(ExamReadinessProjection projection);

    Optional<ExamReadinessProjection> findByUserIdAndExamCode(UUID userId, String examCode);

    List<ExamReadinessProjection> findAllByUserId(UUID userId);

    List<ExamReadinessTopicAllocation> findTopicAllocationsByUserId(UUID userId);

    int deleteByUserId(UUID userId);
}
