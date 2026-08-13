package com.verifiedai.problem.application.parse;

public record NormalizedProblemParse(
    String rawOutputJson,
    String normalizedProblemJson,
    String supportStatus,
    String unsupportedReason,
    boolean reviewRequired
) {
}
