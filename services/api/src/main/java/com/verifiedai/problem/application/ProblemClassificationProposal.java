package com.verifiedai.problem.application;

import com.verifiedai.problem.domain.model.ClassificationDifficulty;
import com.verifiedai.problem.domain.model.ProblemClassificationReviewReason;
import com.verifiedai.problem.domain.model.ProblemClassificationStatus;
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
