package com.verifiedai.problem.infrastructure.preprocessing;

import com.verifiedai.problem.domain.model.ProblemAssetDerivativeKind;
import com.verifiedai.problem.domain.model.ProblemAssetPreprocessingSignalType;
import com.verifiedai.problem.domain.model.ProblemAssetQualityOutcome;
import com.verifiedai.problem.domain.model.ProblemAssetQualitySeverity;
import java.util.List;

public record ProblemAssetImagePreprocessingResult(
    int sourceWidth,
    int sourceHeight,
    boolean orientationNormalized,
    boolean perspectiveApplied,
    boolean contrastNormalized,
    ProblemAssetQualityOutcome qualityOutcome,
    List<DerivativeImage> derivatives,
    List<QualitySignal> qualitySignals
) {
    public ProblemAssetImagePreprocessingResult {
        derivatives = List.copyOf(derivatives);
        qualitySignals = List.copyOf(qualitySignals);
    }

    public record DerivativeImage(
        ProblemAssetDerivativeKind kind,
        byte[] bytes,
        int width,
        int height,
        boolean resized
    ) {
    }

    public record QualitySignal(
        ProblemAssetPreprocessingSignalType signalType,
        ProblemAssetQualitySeverity severity,
        double score,
        double threshold,
        String messageCode
    ) {
    }
}
