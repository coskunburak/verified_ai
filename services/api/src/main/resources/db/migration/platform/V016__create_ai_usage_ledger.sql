CREATE TABLE ai_usage_records (
    id UUID PRIMARY KEY,
    operation_id UUID NOT NULL UNIQUE,

    user_id UUID NULL,
    problem_session_id UUID NULL,

    capability VARCHAR(64) NOT NULL,

    route_policy_version VARCHAR(128) NOT NULL,
    route_id VARCHAR(128) NOT NULL,

    provider VARCHAR(128) NOT NULL,
    model VARCHAR(256) NOT NULL,

    prompt_id VARCHAR(128) NULL,
    prompt_version VARCHAR(64) NULL,
    schema_version VARCHAR(128) NULL,

    provider_request_id VARCHAR(256) NULL,
    provider_response_id VARCHAR(256) NULL,

    status VARCHAR(64) NOT NULL,
    failure_class VARCHAR(64) NULL,
    retryable BOOLEAN NOT NULL DEFAULT FALSE,

    attempt_count INTEGER NOT NULL DEFAULT 0,
    fallback_used BOOLEAN NOT NULL DEFAULT FALSE,
    fallback_chain TEXT NULL,

    escalation_reason VARCHAR(128) NULL,
    cache_status VARCHAR(64) NULL,

    input_token_count INTEGER NULL,
    output_token_count INTEGER NULL,
    image_unit_count INTEGER NULL,
    request_unit_count INTEGER NOT NULL DEFAULT 0,

    estimated_cost_micros BIGINT NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    pricing_version VARCHAR(128) NOT NULL,

    provider_latency_ms BIGINT NOT NULL DEFAULT 0,
    gateway_latency_ms BIGINT NOT NULL DEFAULT 0,

    correlation_id VARCHAR(128) NULL,
    trace_id VARCHAR(128) NULL,

    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NULL,

    CONSTRAINT ck_ai_usage_capability
        CHECK (
            capability IN (
                'VISION_PARSE',
                'PROBLEM_NORMALIZE',
                'PROBLEM_CLASSIFY',
                'SOLVE',
                'ARBITRATE',
                'EXPLAIN',
                'MISTAKE_CLASSIFY',
                'TUTOR',
                'PRACTICE_GENERATE'
            )
        ),

    CONSTRAINT ck_ai_usage_status
        CHECK (
            status IN (
                'STARTED',
                'SUCCEEDED',
                'FAILED_RETRYABLE',
                'FAILED_TERMINAL',
                'DISABLED',
                'BLOCKED_BUDGET',
                'BLOCKED_PROVIDER_UNAVAILABLE',
                'BLOCKED_POLICY'
            )
        ),

    CONSTRAINT ck_ai_usage_attempt_count
        CHECK (attempt_count >= 0),

    CONSTRAINT ck_ai_usage_cost
        CHECK (estimated_cost_micros >= 0),

    CONSTRAINT ck_ai_usage_provider_latency
        CHECK (provider_latency_ms >= 0),

    CONSTRAINT ck_ai_usage_gateway_latency
        CHECK (gateway_latency_ms >= 0),

    CONSTRAINT ck_ai_usage_request_units
        CHECK (request_unit_count >= 0),

    CONSTRAINT ck_ai_usage_input_tokens
        CHECK (
            input_token_count IS NULL
            OR input_token_count >= 0
        ),

    CONSTRAINT ck_ai_usage_output_tokens
        CHECK (
            output_token_count IS NULL
            OR output_token_count >= 0
        ),

    CONSTRAINT ck_ai_usage_image_units
        CHECK (
            image_unit_count IS NULL
            OR image_unit_count >= 0
        )
);

CREATE INDEX idx_ai_usage_records_created_at
    ON ai_usage_records(created_at);

CREATE INDEX idx_ai_usage_records_capability_created_at
    ON ai_usage_records(capability, created_at DESC);

CREATE INDEX idx_ai_usage_records_provider_created_at
    ON ai_usage_records(provider, created_at DESC);

CREATE INDEX idx_ai_usage_records_status_created_at
    ON ai_usage_records(status, created_at DESC);

CREATE INDEX idx_ai_usage_records_user_created_at
    ON ai_usage_records(user_id, created_at DESC)
    WHERE user_id IS NOT NULL;

CREATE INDEX idx_ai_usage_records_problem_session
    ON ai_usage_records(problem_session_id, created_at DESC)
    WHERE problem_session_id IS NOT NULL;
