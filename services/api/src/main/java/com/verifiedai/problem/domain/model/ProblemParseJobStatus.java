package com.verifiedai.problem.domain.model;

public enum ProblemParseJobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED_RETRYABLE,
    FAILED_TERMINAL,
    UNSUPPORTED
}
