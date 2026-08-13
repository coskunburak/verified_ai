package com.verifiedai.problem.application.classification;

import com.verifiedai.problem.domain.model.classification.ClassificationConfidence;
import com.verifiedai.problem.domain.model.classification.ClassificationConfidenceCalibration;

public record ClassificationConfidenceDecision(
    ClassificationConfidence band,
    ClassificationConfidenceCalibration calibration,
    String policyVersion
) {
}
