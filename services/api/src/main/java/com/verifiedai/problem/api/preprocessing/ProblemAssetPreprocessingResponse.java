package com.verifiedai.problem.api.preprocessing;

import com.verifiedai.problem.application.asset.ProblemAssetDerivativeResult;
import com.verifiedai.problem.application.asset.ProblemAssetPreprocessingResult;
import com.verifiedai.problem.application.asset.ProblemAssetQualitySignalResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProblemAssetPreprocessingResponse(
    UUID sourceAssetId,
    UUID problemSessionId,
    String sourceAssetStatus,
    String preprocessingStatus,
    String qualityOutcome,
    String failureCode,
    UUID preferredRecognitionDerivativeId,
    List<ProblemAssetDerivativeResponse> derivatives,
    List<ProblemAssetQualitySignalResponse> qualitySignals,
    List<String> userRecoveryActions,
    Instant completedAt
) {
    static ProblemAssetPreprocessingResponse from(ProblemAssetPreprocessingResult result) {
        return new ProblemAssetPreprocessingResponse(
            result.sourceAssetId(),
            result.problemSessionId(),
            result.sourceAssetStatus(),
            result.preprocessingStatus(),
            result.qualityOutcome(),
            result.failureCode(),
            result.preferredRecognitionDerivativeId(),
            result.derivatives().stream().map(ProblemAssetDerivativeResponse::from).toList(),
            result.qualitySignals().stream().map(ProblemAssetQualitySignalResponse::from).toList(),
            result.userRecoveryActions(),
            result.completedAt()
        );
    }

    public record ProblemAssetDerivativeResponse(
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
        static ProblemAssetDerivativeResponse from(ProblemAssetDerivativeResult derivative) {
            return new ProblemAssetDerivativeResponse(
                derivative.derivativeId(),
                derivative.derivativeKind(),
                derivative.status(),
                derivative.selectedForRecognition(),
                derivative.contentType(),
                derivative.sizeBytes(),
                derivative.checksumAlgorithm(),
                derivative.checksumSha256(),
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

    public record ProblemAssetQualitySignalResponse(
        String signalType,
        String severity,
        BigDecimal score,
        BigDecimal threshold,
        String policyVersion,
        String messageCode
    ) {
        static ProblemAssetQualitySignalResponse from(ProblemAssetQualitySignalResult signal) {
            return new ProblemAssetQualitySignalResponse(
                signal.signalType(),
                signal.severity(),
                signal.score(),
                signal.threshold(),
                signal.policyVersion(),
                signal.messageCode()
            );
        }
    }
}
