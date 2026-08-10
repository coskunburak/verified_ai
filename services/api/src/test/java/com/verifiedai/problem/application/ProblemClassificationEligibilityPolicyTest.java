package com.verifiedai.problem.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class ProblemClassificationEligibilityPolicyTest {

    private ProblemClassificationEligibilityPolicy policy;

    @BeforeEach
    void setUp() {
        policy =
            new ProblemClassificationEligibilityPolicy();
    }

    @Test
    void supportedEquationIsEligible() {
        ProblemClassificationEligibility result =
            policy.evaluate(
                "EQUATION",
                "SOLVE_EQUATION",
                false
            );

        assertThat(
            result.eligible()
        ).isTrue();

        assertThat(
            result.upstreamReviewRequired()
        ).isFalse();

        assertThat(
            result.reason()
        ).isNull();
    }

    @Test
    void invalidCanonicalTypeTaskCombinationIsUnsupported() {
        ProblemClassificationEligibility result =
            policy.evaluate(
                "EQUATION",
                "EVALUATE",
                false
            );

        assertThat(
            result.eligible()
        ).isFalse();

        assertThat(
            result.upstreamReviewRequired()
        ).isFalse();

        assertThat(
            result.reason()
        ).contains(
            "outside classification v1 scope"
        );
    }

    @Test
    void supportedInequalityIsEligible() {
        assertThat(
            policy.evaluate(
                "INEQUALITY",
                "SOLVE_INEQUALITY",
                false
            ).eligible()
        ).isTrue();
    }

    @Test
    void arithmeticEvaluateIsEligible() {
        assertThat(
            policy.evaluate(
                "ARITHMETIC_EXPRESSION",
                "EVALUATE",
                false
            ).eligible()
        ).isTrue();
    }

    @Test
    void algebraSimplifyIsEligible() {
        assertThat(
            policy.evaluate(
                "ALGEBRAIC_EXPRESSION",
                "SIMPLIFY",
                false
            ).eligible()
        ).isTrue();
    }

    @Test
    void upstreamRiskPreventsAiEligibility() {
        ProblemClassificationEligibility result =
            policy.evaluate(
                "EQUATION",
                "SOLVE_EQUATION",
                true
            );

        assertThat(
            result.eligible()
        ).isFalse();

        assertThat(
            result.upstreamReviewRequired()
        ).isTrue();

        assertThat(
            result.reason()
        ).contains(
            "upstream review risk"
        );
    }

    @Test
    void unsupportedProblemTypeIsFirstClassUnsupported() {
        ProblemClassificationEligibility result =
            policy.evaluate(
                "CALCULUS_LIMIT",
                "EVALUATE",
                false
            );

        assertThat(
            result.eligible()
        ).isFalse();

        assertThat(
            result.upstreamReviewRequired()
        ).isFalse();

        assertThat(
            result.reason()
        ).contains(
            "outside classification v1 scope"
        );
    }

    @Test
    void unsupportedTaskTypeIsFirstClassUnsupported() {
        ProblemClassificationEligibility result =
            policy.evaluate(
                "EQUATION",
                "PROVE",
                false
            );

        assertThat(
            result.eligible()
        ).isFalse();

        assertThat(
            result.upstreamReviewRequired()
        ).isFalse();
    }
}
