package com.verifiedai.problem.domain.model.recognition;

public enum RecognitionJobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED_RETRYABLE,
    FAILED_TERMINAL
}
