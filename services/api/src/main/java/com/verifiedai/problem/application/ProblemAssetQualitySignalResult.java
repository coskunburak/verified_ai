package com.verifiedai.problem.application;

import com.verifiedai.problem.infrastructure.persistence.ProblemAssetQualityEvidenceJpaEntity;
import java.math.BigDecimal;

public record ProblemAssetQualitySignalResult(
    String signalType,
    String severity,
    BigDecimal score,
    BigDecimal threshold,
    String policyVersion,
    String messageCode
) {
    static ProblemAssetQualitySignalResult from(ProblemAssetQualityEvidenceJpaEntity evidence) {
        return new ProblemAssetQualitySignalResult(
            evidence.signalType(),
            evidence.severity(),
            evidence.score(),
            evidence.threshold(),
            evidence.policyVersion(),
            evidence.messageCode()
        );
    }
}
