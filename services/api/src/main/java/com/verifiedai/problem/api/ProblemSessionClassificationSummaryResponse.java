package com.verifiedai.problem.api;

import com.verifiedai.problem.application.ProblemSessionClassificationSummary;
import java.util.UUID;

public record ProblemSessionClassificationSummaryResponse(
    UUID classificationId,
    int revision,
    String status,
    String primarySkillId,
    String difficulty,
    String reviewReason
) {
    static ProblemSessionClassificationSummaryResponse from(ProblemSessionClassificationSummary classification) {
        if (classification == null) {
            return null;
        }
        return new ProblemSessionClassificationSummaryResponse(
            classification.classificationId(),
            classification.revision(),
            classification.status(),
            classification.primarySkillId(),
            classification.difficulty(),
            classification.reviewReason()
        );
    }
}
