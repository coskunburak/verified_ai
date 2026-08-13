package com.verifiedai.problem.application.classification;

import com.verifiedai.problem.domain.model.classification.ClassificationDifficulty;
import com.verifiedai.problem.domain.model.classification.ProblemClassificationReviewReason;
import com.verifiedai.problem.domain.model.classification.ProblemClassificationStatus;
import java.util.List;

public record ProblemClassificationProposal(
    String schemaVersion,
    String ontologyVersion,
    ProblemClassificationStatus status,
    String primarySkillId,
    List<String> secondarySkillIds,
    ClassificationDifficulty difficulty,
    ProblemClassificationReviewReason reviewReason
) {
    public ProblemClassificationProposal {
        secondarySkillIds = secondarySkillIds == null
            ? List.of()
            : List.copyOf(secondarySkillIds);
    }
}
