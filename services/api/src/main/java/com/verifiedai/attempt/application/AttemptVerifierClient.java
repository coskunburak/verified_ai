package com.verifiedai.attempt.application;
import com.verifiedai.attempt.domain.AttemptAnalysisResult;
import java.util.UUID;
public interface AttemptVerifierClient {
    default com.verifiedai.attempt.domain.AttemptLearningEvidence learning(UUID id,String action,String request) {
        throw new IllegalStateException("Learning verification is unavailable");
    }
    AttemptAnalysisResult verify(UUID attempt,int stepCount,String request);
}
