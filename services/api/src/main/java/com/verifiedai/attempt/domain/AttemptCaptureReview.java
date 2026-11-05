package com.verifiedai.attempt.domain;
import java.util.*;
/** Learner attestation and transcription provenance, never mathematical or provider verification. */
public record AttemptCaptureReview(String schemaVersion,String engine,List<String> rawLines,boolean confirmed,UUID assetSessionId,UUID recognitionJobId) {
    public AttemptCaptureReview {
        rawLines=List.copyOf(rawLines);
        if(!"attempt-capture-review-v1".equals(schemaVersion) || !Set.of("NATIVE_VISION","GATEWAY_RECOGNITION").contains(engine)
            || !confirmed || rawLines.isEmpty() || rawLines.size()>12 || rawLines.stream().anyMatch(s->!AttemptAnalysisPolicy.validText(s))
            || (engine.equals("GATEWAY_RECOGNITION")!=(assetSessionId!=null && recognitionJobId!=null))
            || (engine.equals("NATIVE_VISION") && (assetSessionId!=null || recognitionJobId!=null))) throw new IllegalArgumentException("Invalid capture review");
    }
}
