package com.verifiedai.problem.domain.model.parse;

public enum ProblemParseJobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED_RETRYABLE,
    FAILED_TERMINAL,
    UNSUPPORTED
}
