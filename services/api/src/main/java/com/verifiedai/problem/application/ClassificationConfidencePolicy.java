package com.verifiedai.problem.application;

import com.verifiedai.problem.domain.model.ClassificationConfidence;
import com.verifiedai.problem.domain.model.ClassificationConfidenceCalibration;
import com.verifiedai.problem.domain.model.ProblemClassificationStatus;
import org.springframework.stereotype.Component;

@Component
final class ClassificationConfidencePolicy {

    ClassificationConfidenceDecision evaluate(
        ValidatedProblemClassification classification,
        boolean upstreamReviewRequired
    ) {
        if (classification == null) {
            throw new IllegalArgumentException(
                "classification is required"
            );
        }

        ClassificationConfidence band =
            switch (classification.status()) {
                case CLASSIFIED -> upstreamReviewRequired
                    ? ClassificationConfidence.LOW
                    : ClassificationConfidence.MEDIUM;

                case REVIEW_REQUIRED ->
                    ClassificationConfidence.LOW;

                case UNKNOWN, UNSUPPORTED ->
                    ClassificationConfidence.UNKNOWN;
            };

        /*
         * V1 deliberately never promotes an uncalibrated result to HIGH.
         *
         * Sprint 4.10 or a later calibration release may introduce a
         * calibrated policy backed by measurable evaluation evidence.
         */
        return new ClassificationConfidenceDecision(
            band,
            ClassificationConfidenceCalibration.UNCALIBRATED,
            ProblemClassificationContract.CONFIDENCE_POLICY_VERSION
        );
    }
}
