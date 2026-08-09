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
    UNKNOWN
}
