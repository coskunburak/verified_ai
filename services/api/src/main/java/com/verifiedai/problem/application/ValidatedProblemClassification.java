package com.verifiedai.problem.application;

import com.verifiedai.problem.domain.model.ClassificationDifficulty;
import com.verifiedai.problem.domain.model.ProblemClassificationReviewReason;
import com.verifiedai.problem.domain.model.ProblemClassificationStatus;
import java.util.List;

public record ValidatedProblemClassification(
    ProblemClassificationStatus status,
    ProblemClassificationReviewReason reviewReason,
    String ontologyVersion,
    String subjectId,
    String topicId,
    String primarySkillId,
    List<String> secondarySkillIds,
    ClassificationDifficulty difficulty
) {
    public ValidatedProblemClassification {
        secondarySkillIds = secondarySkillIds == null
            ? List.of()
            : List.copyOf(secondarySkillIds);
    }
}
