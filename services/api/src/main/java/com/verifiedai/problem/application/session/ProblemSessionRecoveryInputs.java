package com.verifiedai.problem.application.session;

import com.verifiedai.problem.infrastructure.persistence.entity.CanonicalProblemJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.entity.ProblemAssetDerivativeJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.entity.ProblemAssetJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.entity.ProblemClassificationJobJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.entity.ProblemClassificationJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.entity.ProblemParseJobJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.entity.ProblemParseJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.entity.ProblemSessionJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.entity.RecognitionEvidenceJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.entity.RecognitionJobJpaEntity;

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
