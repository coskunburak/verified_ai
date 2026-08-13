package com.verifiedai.ai.application;

public enum AiExecutionStatus {
    SUCCEEDED,

    FAILED_RETRYABLE,
    FAILED_TERMINAL,

    DISABLED,

    BLOCKED_BUDGET,
    BLOCKED_PROVIDER_UNAVAILABLE,
    BLOCKED_POLICY
}
