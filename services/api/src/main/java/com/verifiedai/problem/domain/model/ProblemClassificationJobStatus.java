package com.verifiedai.problem.domain.model;

public enum ProblemClassificationJobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED_RETRYABLE,
    FAILED_TERMINAL
}
