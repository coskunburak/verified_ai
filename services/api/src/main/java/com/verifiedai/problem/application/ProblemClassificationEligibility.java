package com.verifiedai.problem.application;

public record ProblemClassificationEligibility(
    boolean eligible,
    boolean upstreamReviewRequired,
    String reason
) {
    static ProblemClassificationEligibility eligible() {
        return new ProblemClassificationEligibility(
            true,
            false,
            null
        );
    }

    static ProblemClassificationEligibility reviewRequired(
        String reason
    ) {
        return new ProblemClassificationEligibility(
            false,
            true,
            reason
        );
    }

    static ProblemClassificationEligibility unsupported(
        String reason
    ) {
        return new ProblemClassificationEligibility(
            false,
            false,
            reason
        );
    }
}
