package com.verifiedai.problem.application.classification;

import com.verifiedai.problem.domain.model.classification.ClassificationDifficulty;
import com.verifiedai.problem.domain.model.classification.ProblemClassificationReviewReason;
import com.verifiedai.problem.domain.model.classification.ProblemClassificationStatus;
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
