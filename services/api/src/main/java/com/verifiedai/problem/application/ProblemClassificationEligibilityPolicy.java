package com.verifiedai.problem.application;

import java.util.Set;
import org.springframework.stereotype.Component;

@Component
final class ProblemClassificationEligibilityPolicy {

    private static final Set<String> SUPPORTED_PROBLEM_TYPES =
        Set.of(
            "ARITHMETIC_EXPRESSION",
            "ALGEBRAIC_EXPRESSION",
            "EQUATION",
            "INEQUALITY"
        );

    private static final Set<String> SUPPORTED_TASK_TYPES =
        Set.of(
            "EVALUATE",
            "SIMPLIFY",
            "SOLVE_EQUATION",
            "SOLVE_INEQUALITY"
        );

    ProblemClassificationEligibility evaluate(
        String problemType,
        String taskType,
        boolean upstreamReviewRequired
    ) {
        if (
            problemType == null
                || !SUPPORTED_PROBLEM_TYPES.contains(problemType)
        ) {
            return ProblemClassificationEligibility.unsupportedOutcome(
                "Canonical problem type is outside classification v1 scope"
            );
        }

        if (
            taskType == null
                || !SUPPORTED_TASK_TYPES.contains(taskType)
        ) {
            return ProblemClassificationEligibility.unsupportedOutcome(
                "Canonical task type is outside classification v1 scope"
            );
        }

        if (upstreamReviewRequired) {
            return ProblemClassificationEligibility.reviewRequiredOutcome(
                "Canonical problem carries upstream review risk"
            );
        }

        return ProblemClassificationEligibility.eligibleOutcome();
    }
}
