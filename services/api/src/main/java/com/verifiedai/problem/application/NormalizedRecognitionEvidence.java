package com.verifiedai.problem.application;

import java.util.List;

record NormalizedRecognitionEvidence(
    String rawOutputJson,
    String normalizedEvidenceJson,
    String upstreamQualityEvidenceJson,
    boolean reviewRequired,
    int blockCount,
    List<RecognitionBlockResult> blocks
) {
}
