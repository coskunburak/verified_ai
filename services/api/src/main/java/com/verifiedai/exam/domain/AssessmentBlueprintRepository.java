package com.verifiedai.exam.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssessmentBlueprintRepository {

    AssessmentBlueprint save(AssessmentBlueprint blueprint);

    Optional<AssessmentBlueprint> findById(UUID id);

    Optional<AssessmentBlueprint> findByBlueprintCode(String blueprintCode);

    List<AssessmentBlueprint> findByExamDefinition(
            UUID examDefinitionId,
            Integer examVersionNumber,
            AssessmentBlueprintStatus status,
            AssessmentBlueprintMode blueprintMode
    );
}
