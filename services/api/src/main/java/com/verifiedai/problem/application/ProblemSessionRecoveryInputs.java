package com.verifiedai.problem.application;

import com.verifiedai.problem.infrastructure.persistence.CanonicalProblemJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.ProblemAssetDerivativeJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.ProblemAssetJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.ProblemClassificationJobJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.ProblemClassificationJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.ProblemParseJobJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.ProblemParseJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.ProblemSessionJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.RecognitionEvidenceJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.RecognitionJobJpaEntity;

public record ProblemSessionRecoveryInputs(
    ProblemSessionJpaEntity session,
    ProblemAssetJpaEntity availableSourceAsset,
    ProblemAssetDerivativeJpaEntity recognitionDerivative,
    RecognitionJobJpaEntity recognitionJob,
    RecognitionEvidenceJpaEntity recognitionEvidence,
    ProblemParseJobJpaEntity parseJob,
    ProblemParseJpaEntity selectedParse,
    boolean acceptedParseHistoryExists,
    CanonicalProblemJpaEntity canonicalProblem,
    ProblemClassificationJobJpaEntity classificationJob,
    ProblemClassificationJpaEntity classification
) {
}
