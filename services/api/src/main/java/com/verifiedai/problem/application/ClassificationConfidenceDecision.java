package com.verifiedai.problem.application;

import com.verifiedai.problem.domain.model.ClassificationConfidence;
import com.verifiedai.problem.domain.model.ClassificationConfidenceCalibration;

public record ClassificationConfidenceDecision(
    ClassificationConfidence band,
    ClassificationConfidenceCalibration calibration,
    String policyVersion
) {
}
