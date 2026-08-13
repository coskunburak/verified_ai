package com.verifiedai.problem.application.classification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProblemClassificationResult(
    UUID classificationId,
    UUID canonicalProblemId,
    UUID problemSessionId,
    String status,
    String ontologyVersion,
    String subjectId,
    String topicId,
    String primarySkillId,
    List<String> secondarySkillIds,
    String difficulty,
    String confidence,
    Instant createdAt
) {
    public ProblemClassificationResult {
        secondarySkillIds = secondarySkillIds == null ? List.of() : List.copyOf(secondarySkillIds);
    }
}
