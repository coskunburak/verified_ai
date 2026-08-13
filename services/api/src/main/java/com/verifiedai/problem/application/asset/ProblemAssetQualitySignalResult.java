package com.verifiedai.problem.application.asset;

import com.verifiedai.problem.infrastructure.persistence.entity.ProblemAssetQualityEvidenceJpaEntity;
import java.math.BigDecimal;

public record ProblemAssetQualitySignalResult(
    String signalType,
    String severity,
    BigDecimal score,
    BigDecimal threshold,
    String policyVersion,
    String messageCode
) {
    public static ProblemAssetQualitySignalResult from(ProblemAssetQualityEvidenceJpaEntity evidence) {
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
