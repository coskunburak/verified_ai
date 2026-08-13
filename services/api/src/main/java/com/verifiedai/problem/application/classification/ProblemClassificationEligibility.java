package com.verifiedai.problem.application.classification;

public record ProblemClassificationEligibility(
    boolean eligible,
    boolean upstreamReviewRequired,
    String reason
) {

    static ProblemClassificationEligibility eligibleOutcome() {
        return new ProblemClassificationEligibility(
            true,
            false,
            null
        );
    }

    static ProblemClassificationEligibility reviewRequiredOutcome(
        String reason
    ) {
        return new ProblemClassificationEligibility(
            false,
            true,
            reason
        );
    }

    static ProblemClassificationEligibility unsupportedOutcome(
        String reason
    ) {
        return new ProblemClassificationEligibility(
            false,
            false,
            reason
        );
    }
}
