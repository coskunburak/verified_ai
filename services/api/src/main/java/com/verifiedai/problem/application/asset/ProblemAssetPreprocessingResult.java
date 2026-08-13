package com.verifiedai.problem.application.asset;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProblemAssetPreprocessingResult(
    UUID sourceAssetId,
    UUID problemSessionId,
    String sourceAssetStatus,
    String preprocessingStatus,
    String qualityOutcome,
    String failureCode,
    UUID preferredRecognitionDerivativeId,
    List<ProblemAssetDerivativeResult> derivatives,
    List<ProblemAssetQualitySignalResult> qualitySignals,
    List<String> userRecoveryActions,
    Instant completedAt
) {
    public ProblemAssetPreprocessingResult {
        derivatives = List.copyOf(derivatives);
        qualitySignals = List.copyOf(qualitySignals);
        userRecoveryActions = List.copyOf(userRecoveryActions);
    }
}
