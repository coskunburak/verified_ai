package com.verifiedai.exam.application;

import com.verifiedai.exam.domain.ExamModels.ExamDefinition;
import com.verifiedai.exam.domain.ExamModels.ExamFamily;
import com.verifiedai.exam.domain.ExamModels.Jurisdiction;
import com.verifiedai.exam.domain.ExamModels.StudentExamTargetRecord;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence boundary for exam definitions, curriculum topic weights, and student exam targets.
 */
public interface ExamRepository {

    List<ExamDefinition> findCatalog(Jurisdiction jurisdiction, ExamFamily examFamily, boolean includeDeprecated);

    Optional<ExamDefinition> findById(UUID examDefinitionId);

    Optional<ExamDefinition> findLatestByCode(String examCode, boolean includeDeprecated);

    Optional<ExamDefinition> findByCodeAndVersion(String examCode, String examVersion);

    Optional<StudentExamTargetRecord> findTargetByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    Optional<StudentExamTargetRecord> findTargetById(UUID targetId);

    List<StudentExamTargetRecord> findTargetsByUserId(UUID userId);

    int supersedeActivePrimaryTargets(UUID userId, Instant supersededAt);

    StudentExamTargetRecord insertTarget(StudentExamTargetRecord record);

    int deleteTargetsByUserId(UUID userId);
}
