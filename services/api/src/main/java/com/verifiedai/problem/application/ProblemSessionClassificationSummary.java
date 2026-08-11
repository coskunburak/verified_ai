package com.verifiedai.problem.application;

import java.util.UUID;

public record ProblemSessionClassificationSummary(
    UUID classificationId,
    int revision,
    String status,
    String primarySkillId,
    String difficulty,
    String reviewReason
) {
}
