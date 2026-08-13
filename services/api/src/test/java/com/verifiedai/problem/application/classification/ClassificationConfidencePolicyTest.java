package com.verifiedai.problem.application.classification;

import com.verifiedai.problem.domain.model.classification.ClassificationConfidence;
import com.verifiedai.problem.domain.model.classification.ClassificationConfidenceCalibration;
import com.verifiedai.problem.domain.model.classification.ClassificationDifficulty;
import com.verifiedai.problem.domain.model.classification.ProblemClassificationReviewReason;
import com.verifiedai.problem.domain.model.classification.ProblemClassificationStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

final class ClassificationConfidencePolicyTest {

    private ClassificationConfidencePolicy policy;

    @BeforeEach
    void setUp() {
        policy =
            new ClassificationConfidencePolicy();
    }

    @Test
    void classifiedWithoutUpstreamRiskIsMedium() {
        ClassificationConfidenceDecision decision =
            policy.evaluate(
                classified(),
                false
            );

        assertDecision(
            decision,
            ClassificationConfidence.MEDIUM
        );
    }

    @Test
    void classifiedWithUpstreamRiskIsLow() {
        ClassificationConfidenceDecision decision =
            policy.evaluate(
                classified(),
                true
            );

        assertDecision(
            decision,
            ClassificationConfidence.LOW
        );
    }

    @Test
    void reviewRequiredIsLow() {
        ClassificationConfidenceDecision decision =
            policy.evaluate(
                nonClassified(
                    ProblemClassificationStatus
                        .REVIEW_REQUIRED,
                    ProblemClassificationReviewReason
                        .UPSTREAM_RISK
                ),
                true
            );

        assertDecision(
            decision,
            ClassificationConfidence.LOW
        );
    }

    @Test
    void unknownIsUnknown() {
        ClassificationConfidenceDecision decision =
            policy.evaluate(
                nonClassified(
                    ProblemClassificationStatus.UNKNOWN,
                    null
                ),
                false
            );

        assertDecision(
            decision,
            ClassificationConfidence.UNKNOWN
        );
    }

    @Test
    void unsupportedIsUnknown() {
        ClassificationConfidenceDecision decision =
            policy.evaluate(
                nonClassified(
                    ProblemClassificationStatus
                        .UNSUPPORTED,
                    null
                ),
                false
            );

        assertDecision(
            decision,
            ClassificationConfidence.UNKNOWN
        );
    }

    @Test
    void v1NeverProducesHighConfidence() {
        List<ClassificationConfidenceDecision> decisions =
            List.of(
                policy.evaluate(
                    classified(),
                    false
                ),
                policy.evaluate(
                    classified(),
                    true
                ),
                policy.evaluate(
                    nonClassified(
                        ProblemClassificationStatus
                            .REVIEW_REQUIRED,
                        ProblemClassificationReviewReason
                            .UPSTREAM_RISK
                    ),
                    true
                ),
                policy.evaluate(
                    nonClassified(
                        ProblemClassificationStatus.UNKNOWN,
                        null
                    ),
                    false
                ),
                policy.evaluate(
                    nonClassified(
                        ProblemClassificationStatus
                            .UNSUPPORTED,
                        null
                    ),
                    false
                )
            );

        assertThat(
            decisions
        ).allSatisfy(
            decision ->
                assertThat(
                    decision.band()
                ).isNotEqualTo(
                    ClassificationConfidence.HIGH
                )
        );
    }

    private static ValidatedProblemClassification classified() {
        return new ValidatedProblemClassification(
            ProblemClassificationStatus.CLASSIFIED,
            null,
            ProblemClassificationTestCatalog
                .ONTOLOGY_VERSION,
            ProblemClassificationTestCatalog.MATH,
            ProblemClassificationTestCatalog.EQUATIONS,
            ProblemClassificationTestCatalog
                .LINEAR_ONE_VARIABLE,
            List.of(),
            ClassificationDifficulty.MEDIUM
        );
    }

    private static ValidatedProblemClassification nonClassified(
        ProblemClassificationStatus status,
        ProblemClassificationReviewReason reviewReason
    ) {
        return new ValidatedProblemClassification(
            status,
            reviewReason,
            ProblemClassificationTestCatalog
                .ONTOLOGY_VERSION,
            null,
            null,
            null,
            List.of(),
            null
        );
    }

    private static void assertDecision(
        ClassificationConfidenceDecision decision,
        ClassificationConfidence expectedBand
    ) {
        assertThat(
            decision.band()
        ).isEqualTo(
            expectedBand
        );

        assertThat(
            decision.calibration()
        ).isEqualTo(
            ClassificationConfidenceCalibration
                .UNCALIBRATED
        );

        assertThat(
            decision.policyVersion()
        ).isEqualTo(
            ProblemClassificationContract
                .CONFIDENCE_POLICY_VERSION
        );
    }
}
