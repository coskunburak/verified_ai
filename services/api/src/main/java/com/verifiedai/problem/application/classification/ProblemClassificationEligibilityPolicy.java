package com.verifiedai.problem.application.classification;

import java.util.Set;
import org.springframework.stereotype.Component;

@Component
final class ProblemClassificationEligibilityPolicy {

    private static final Set<String>
        SUPPORTED_CLASSIFICATION_KEYS =
        Set.of(
            "ARITHMETIC_EXPRESSION:EVALUATE",
            "ARITHMETIC_EXPRESSION:SIMPLIFY",
            "ALGEBRAIC_EXPRESSION:EVALUATE",
            "ALGEBRAIC_EXPRESSION:SIMPLIFY",
            "EQUATION:SOLVE_EQUATION",
            "INEQUALITY:SOLVE_INEQUALITY"
        );

    ProblemClassificationEligibility evaluate(
        String problemType,
        String taskType,
        boolean upstreamReviewRequired
    ) {
        if (
            problemType == null
                || problemType.isBlank()
                || taskType == null
                || taskType.isBlank()
        ) {
            return ProblemClassificationEligibility
                .unsupportedOutcome(
                    "Canonical problem type/task pair is outside classification v1 scope"
                );
        }

        String classificationKey =
            problemType + ":" + taskType;

        if (
            !SUPPORTED_CLASSIFICATION_KEYS
                .contains(classificationKey)
        ) {
            return ProblemClassificationEligibility
                .unsupportedOutcome(
                    "Canonical problem type/task pair is outside classification v1 scope"
                );
        }

        /*
         * Upstream review risk is evaluated only after
         * confirming that the canonical type/task pair
         * belongs to the supported v1 classification surface.
         *
         * This ensures unsupported inputs remain
         * UNSUPPORTED instead of being mislabeled as
         * REVIEW_REQUIRED.
         */
        if (upstreamReviewRequired) {
            return ProblemClassificationEligibility
                .reviewRequiredOutcome(
                    "Canonical problem carries upstream review risk"
                );
        }

        return ProblemClassificationEligibility
            .eligibleOutcome();
    }
}
