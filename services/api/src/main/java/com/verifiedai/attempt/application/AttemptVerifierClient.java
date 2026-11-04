package com.verifiedai.attempt.application;
import com.verifiedai.attempt.domain.AttemptAnalysisResult;
import java.util.UUID;
public interface AttemptVerifierClient {
    enum ComparisonFailureReason { TIMEOUT, ADMISSION_LIMIT, WORKER_FAILURE, INVALID_RESPONSE }
    final class ComparisonFailure extends IllegalStateException {
        private final ComparisonFailureReason reason;
        public ComparisonFailure(ComparisonFailureReason reason) { super("Method verifier unavailable"); this.reason=reason; }
        public ComparisonFailureReason reason() { return reason; }
    }
    default com.verifiedai.attempt.domain.AttemptLearningEvidence learning(UUID id,String action,String request) {
        throw new IllegalStateException("Learning verification is unavailable");
    }
    default com.verifiedai.attempt.domain.MethodComparisonEvidence comparison(UUID id,String request) {
        throw new IllegalStateException("Method comparison unavailable");
    }
    AttemptAnalysisResult verify(UUID attempt,int stepCount,String request);
}
