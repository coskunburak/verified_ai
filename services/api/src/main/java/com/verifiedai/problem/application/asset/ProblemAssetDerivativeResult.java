package com.verifiedai.problem.application.asset;

import com.verifiedai.problem.infrastructure.persistence.entity.ProblemAssetDerivativeJpaEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProblemAssetDerivativeResult(
    UUID derivativeId,
    String derivativeKind,
    String status,
    boolean selectedForRecognition,
    String contentType,
    Long sizeBytes,
    String checksumAlgorithm,
    String checksumSha256,
    Integer width,
    Integer height,
    int sourceWidth,
    int sourceHeight,
    BigDecimal cropX,
    BigDecimal cropY,
    BigDecimal cropWidth,
    BigDecimal cropHeight,
    String processorName,
    String processorVersion,
    String configurationVersion,
    boolean orientationNormalized,
    boolean perspectiveApplied,
    boolean contrastNormalized,
    boolean resized,
    String qualityOutcome,
    String failureCode,
    Instant createdAt,
    Instant completedAt
) {
    static ProblemAssetDerivativeResult from(ProblemAssetDerivativeJpaEntity derivative) {
        return new ProblemAssetDerivativeResult(
            derivative.id(),
            derivative.derivativeKind(),
            derivative.status(),
            derivative.selectedForRecognition(),
            derivative.contentType(),
            derivative.sizeBytes(),
            derivative.checksumAlgorithm(),
            derivative.checksumValue(),
            derivative.width(),
            derivative.height(),
            derivative.sourceWidth(),
            derivative.sourceHeight(),
            derivative.cropX(),
            derivative.cropY(),
            derivative.cropWidth(),
            derivative.cropHeight(),
            derivative.processorName(),
            derivative.processorVersion(),
            derivative.configurationVersion(),
            derivative.orientationNormalized(),
            derivative.perspectiveApplied(),
            derivative.contrastNormalized(),
            derivative.resized(),
            derivative.qualityOutcome(),
            derivative.failureCode(),
            derivative.createdAt(),
            derivative.completedAt()
        );
    }
}
