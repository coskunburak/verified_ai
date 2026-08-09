package com.verifiedai.problem.domain.model;

public enum RecognitionJobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED_RETRYABLE,
    FAILED_TERMINAL
}
