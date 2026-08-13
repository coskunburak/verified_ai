package com.verifiedai.ai.application;

public enum AiProviderFailureClass {

    PROVIDER_UNAVAILABLE,
    TIMEOUT,
    RATE_LIMITED,
    INVALID_AUTH,
    UNSUPPORTED_PAYLOAD,
    OUTPUT_TOO_LARGE,
    SCHEMA_INVALID,
    CONFIGURATION_DISABLED,

    /*
     * Gateway-level failures.
     */
    BUDGET_EXCEEDED,
    POLICY_BLOCKED,
    LEDGER_UNAVAILABLE,
    PROVIDER_NOT_REGISTERED,

    UNKNOWN
}
