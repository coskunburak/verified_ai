package com.verifiedai.problem.application;

public record NormalizedProblemParse(
    String rawOutputJson,
    String normalizedProblemJson,
    String supportStatus,
    String unsupportedReason,
    boolean reviewRequired
) {
}
