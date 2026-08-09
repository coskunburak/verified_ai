package com.verifiedai.problem.api;

import com.verifiedai.problem.application.RecognitionBlockResult;
import com.verifiedai.problem.application.RecognitionBoundingBoxResult;
import com.verifiedai.problem.application.RecognitionStatusResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

record ProblemRecognitionResponse(
    UUID recognitionJobId,
    UUID problemSessionId,
    UUID sourceAssetId,
    UUID inputDerivativeId,
    String status,
    String capability,
    int attemptCount,
    int maxAttempts,
    String lastErrorCode,
    String lastFailureClass,
    boolean reviewRequired,
    String schemaVersion,
    String promptId,
    String promptVersion,
    String routePolicyVersion,
    String provider,
    String model,
    int blockCount,
    List<RecognitionBlockResponse> blocks,
    Instant createdAt,
    Instant updatedAt,
    Instant completedAt
) {
    static ProblemRecognitionResponse from(RecognitionStatusResult result) {
        return new ProblemRecognitionResponse(
            result.recognitionJobId(),
            result.problemSessionId(),
            result.sourceAssetId(),
            result.inputDerivativeId(),
            result.status(),
            result.capability(),
            result.attemptCount(),
            result.maxAttempts(),
            result.lastErrorCode(),
            result.lastFailureClass(),
            result.reviewRequired(),
            result.schemaVersion(),
            result.promptId(),
            result.promptVersion(),
            result.routePolicyVersion(),
            result.provider(),
            result.model(),
            result.blockCount(),
            result.blocks().stream().map(RecognitionBlockResponse::from).toList(),
            result.createdAt(),
            result.updatedAt(),
            result.completedAt()
        );
    }

    record RecognitionBlockResponse(
        String id,
        String kind,
        String text,
        RecognitionBoundingBoxResponse boundingBox,
        int readingOrder,
        String confidenceStatus,
        BigDecimal normalizedConfidence,
        List<String> uncertainty,
        List<String> layoutHints
    ) {
        static RecognitionBlockResponse from(RecognitionBlockResult block) {
            return new RecognitionBlockResponse(
                block.id(),
                block.kind(),
                block.text(),
                RecognitionBoundingBoxResponse.from(block.boundingBox()),
                block.readingOrder(),
                block.confidenceStatus(),
                block.normalizedConfidence(),
                block.uncertainty(),
                block.layoutHints()
            );
        }
    }

    record RecognitionBoundingBoxResponse(
        BigDecimal x,
        BigDecimal y,
        BigDecimal width,
        BigDecimal height
    ) {
        static RecognitionBoundingBoxResponse from(RecognitionBoundingBoxResult boundingBox) {
            return new RecognitionBoundingBoxResponse(
                boundingBox.x(),
                boundingBox.y(),
                boundingBox.width(),
                boundingBox.height()
            );
        }
    }
}
