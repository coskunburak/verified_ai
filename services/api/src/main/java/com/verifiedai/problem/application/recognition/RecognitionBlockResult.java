package com.verifiedai.problem.application.recognition;

import java.math.BigDecimal;
import java.util.List;

public record RecognitionBlockResult(
    String id,
    String kind,
    String text,
    RecognitionBoundingBoxResult boundingBox,
    int readingOrder,
    String confidenceStatus,
    BigDecimal normalizedConfidence,
    List<String> uncertainty,
    List<String> layoutHints
) {
}
